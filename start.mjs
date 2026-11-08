import fs from 'fs';
import net from 'net';
import { spawn, execSync } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const envPath = path.resolve(__dirname, '.env');
const envExamplePath = path.resolve(__dirname, '.env.example');
const isWin = /^win/.test(process.platform);

// 1. Auto-create .env from .env.example on new machines
if (!fs.existsSync(envPath) && fs.existsSync(envExamplePath)) {
  fs.copyFileSync(envExamplePath, envPath);
  console.log('✅ Đã tự động khởi tạo file .env từ .env.example cho máy mới.');
}

let envFile = '';
try {
  envFile = fs.readFileSync(envPath, 'utf8');
} catch {
  console.warn('⚠️ Không tìm thấy file .env ở thư mục gốc, sử dụng cấu hình mặc định.');
}

// 2. Parse .env format
const envVars = { ...process.env };
envFile.split('\n').forEach(line => {
  const trimmed = line.trim();
  if (!trimmed || trimmed.startsWith('#')) return;
  const match = trimmed.match(/^([^=]+)=(.*)$/);
  if (match) {
    envVars[match[1].trim()] = match[2].trim();
  }
});

const FE_PORT = envVars.FRONTEND_PORT || '3005';
const BE_PORT = envVars.BACKEND_PORT || '8085';
const NGROK_DOMAIN = (envVars.NGROK_DOMAIN || '').trim();

// Interpolate variables like ${BACKEND_PORT}
Object.keys(envVars).forEach(key => {
  if (typeof envVars[key] === 'string' && envVars[key].includes('${BACKEND_PORT}')) {
    envVars[key] = envVars[key].replace(/\$\{BACKEND_PORT\}/g, BE_PORT);
  }
});

// Expose runtime helpers to Next.js client
envVars.NEXT_PUBLIC_BACKEND_PORT = BE_PORT;
if (NGROK_DOMAIN && !envVars.NEXT_PUBLIC_NGROK_DOMAIN) {
  envVars.NEXT_PUBLIC_NGROK_DOMAIN = NGROK_DOMAIN;
}

// 3. Auto-install node_modules if missing on a new machine
const nodeModulesPath = path.join(__dirname, 'node_modules');
if (!fs.existsSync(nodeModulesPath)) {
  console.log('📦 Chưa có node_modules trên máy này. Đang tự động chạy "npm install"...');
  execSync(isWin ? 'npm.cmd install' : 'npm install', {
    cwd: __dirname,
    stdio: 'inherit'
  });
}

// 4. Verify Java is installed
try {
  execSync('java -version', { stdio: 'ignore' });
} catch {
  console.error('❌ LỖI: Máy chưa cài đặt Java (JDK 17+). Vui lòng cài đặt JDK 17 trước khi chạy Backend.');
}

// Ensure mvnw is executable on Linux/macOS
if (!isWin) {
  try {
    fs.chmodSync(path.join(__dirname, 'backend', 'mvnw'), 0o755);
  } catch {}
}

// 5. Check MongoDB connection if using local MongoDB
const mongoUri = envVars.SPRING_DATA_MONGODB_URI || 'mongodb://localhost:27017/vtmen';
const isLocalMongo = mongoUri.includes('localhost:') || mongoUri.includes('127.0.0.1:');

function checkPortListening(port, host = '127.0.0.1', timeoutMs = 1500) {
  return new Promise(resolve => {
    const socket = new net.Socket();
    let done = false;
    const finish = (ok) => {
      if (!done) {
        done = true;
        socket.destroy();
        resolve(ok);
      }
    };
    socket.setTimeout(timeoutMs);
    socket.once('connect', () => finish(true));
    socket.once('timeout', () => finish(false));
    socket.once('error', () => finish(false));
    socket.connect(port, host);
  });
}

async function ensureMongoRunning() {
  if (!isLocalMongo) return;
  const isUp = await checkPortListening(27017);
  if (isUp) {
    console.log('✅ MongoDB đang hoạt động tại localhost:27017');
    return;
  }

  console.warn('⚠️ Không thấy MongoDB phản hồi ở cổng 27017.');
  if (isWin) {
    try {
      console.log('🔄 Đang thử bật dịch vụ Windows MongoDB...');
      execSync('net start MongoDB', { stdio: 'ignore' });
      console.log('✅ Đã bật dịch vụ MongoDB thành công.');
      return;
    } catch {
      console.warn(
        '⚠️ Không thể tự bật dịch vụ MongoDB (cần quyền Admin hoặc chưa cài MongoDB).\n' +
        '   👉 Hãy bật Service "MongoDB" trong Windows Services, hoặc chạy: docker compose up -d'
      );
    }
  } else {
    console.warn('   👉 Hãy khởi động MongoDB local hoặc chạy: docker compose up -d');
  }
}

const freePort = (port) => {
  if (isWin) {
    try {
      const out = execSync(`netstat -ano | findstr ":${port} " | findstr "LISTENING"`, { encoding: 'utf8' });
      const pids = new Set();
      for (const line of out.trim().split('\n')) {
        const parts = line.trim().split(/\s+/);
        const pid = parts[parts.length - 1];
        if (pid && pid !== '0' && pid !== String(process.pid)) pids.add(pid);
      }
      for (const pid of pids) {
        try { execSync(`taskkill /f /t /pid ${pid}`, { stdio: 'ignore' }); } catch {}
      }
    } catch {}
  } else {
    try {
      execSync(`lsof -ti:${port} | xargs kill -9`, { stdio: 'ignore' });
    } catch {}
  }
};

await ensureMongoRunning();

const withTunnel = process.argv.includes('--tunnel') || process.env.START_TUNNEL === 'true';
let tunnel = null;

if (withTunnel) {
  console.log(`🚀 Starting Ngrok Tunnel on port ${BE_PORT}...`);
  if (isWin) {
    try {
      execSync('taskkill /f /im ngrok.exe', { stdio: 'ignore' });
    } catch {}
  }
  const policyArg = fs.existsSync(path.join(__dirname, 'ngrok-policy.yml'))
    ? ['--traffic-policy-file', 'ngrok-policy.yml']
    : [];
  const domainArg = NGROK_DOMAIN ? [`--url=${NGROK_DOMAIN}`] : [];
  tunnel = spawn('ngrok', ['http', ...domainArg, ...policyArg, String(BE_PORT)], {
    stdio: 'ignore',
    shell: true
  });
}

// Ensure ports are freed before launching
freePort(BE_PORT);
freePort(FE_PORT);

console.log(`🚀 Starting Frontend on http://localhost:${FE_PORT} ...`);
const feArgs = ['run', 'dev', '--', '-p', String(FE_PORT)];
const fe = spawn(isWin ? 'npm.cmd' : 'npm', feArgs, {
  stdio: 'inherit',
  shell: true,
  env: { ...envVars, PORT: String(FE_PORT) }
});

console.log(`🚀 Starting Backend on http://localhost:${BE_PORT} ...`);
const be = spawn(isWin ? '.\\mvnw.cmd' : './mvnw', ['spring-boot:run'], {
  cwd: path.join(__dirname, 'backend'),
  stdio: 'inherit',
  shell: true,
  env: envVars
});

let cleaningUp = false;
const cleanup = () => {
  if (cleaningUp) return;
  cleaningUp = true;
  console.log('\n🛑 Đang dừng các tiến trình...');
  if (fe && fe.pid) {
    if (isWin) {
      try { execSync(`taskkill /f /t /pid ${fe.pid}`, { stdio: 'ignore' }); } catch {}
    } else {
      fe.kill();
    }
  }
  if (be && be.pid) {
    if (isWin) {
      try { execSync(`taskkill /f /t /pid ${be.pid}`, { stdio: 'ignore' }); } catch {}
    } else {
      be.kill();
    }
  }
  if (tunnel && tunnel.pid) {
    if (isWin) {
      try { execSync(`taskkill /f /t /pid ${tunnel.pid}`, { stdio: 'ignore' }); } catch {}
    } else {
      tunnel.kill();
    }
  }
  if (isWin && withTunnel) {
    try { execSync('taskkill /f /im ngrok.exe', { stdio: 'ignore' }); } catch {}
  }
  freePort(BE_PORT);
  freePort(FE_PORT);
  process.exit();
};

process.on('SIGINT', cleanup);
process.on('SIGTERM', cleanup);
process.on('exit', cleanup);
