import fs from 'fs';
import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const envPath = path.resolve(__dirname, '.env');
let envFile = '';

try {
  envFile = fs.readFileSync(envPath, 'utf8');
} catch (e) {
  console.warn('No .env file found at root, using default environment variables.');
}

// Parse simple .env format
const envVars = { ...process.env };
envFile.split('\n').forEach(line => {
  const trimmed = line.trim();
  if (!trimmed || trimmed.startsWith('#')) return;
  const match = trimmed.match(/^([^=]+)=(.*)$/);
  if (match) {
    envVars[match[1].trim()] = match[2].trim();
  }
});

const FE_PORT = envVars.FRONTEND_PORT || 3005;
const BE_PORT = envVars.BACKEND_PORT || 8085;

// Interpolate variables like ${BACKEND_PORT} in our simple loader
Object.keys(envVars).forEach(key => {
  if (typeof envVars[key] === 'string' && envVars[key].includes('${BACKEND_PORT}')) {
    envVars[key] = envVars[key].replace(/\$\{BACKEND_PORT\}/g, BE_PORT);
  }
});

const isWin = /^win/.test(process.platform);

console.log(`🚀 Starting Frontend on port ${FE_PORT}...`);
// Pass PORT env var so Next.js uses it natively, and also pass -p just in case
const feArgs = ['run', 'dev', '--', '-p', FE_PORT];
const fe = spawn(isWin ? 'npm.cmd' : 'npm', feArgs, { 
  stdio: 'inherit', 
  shell: true,
  env: { ...envVars, PORT: FE_PORT } 
});

console.log(`🚀 Starting Backend...`);
const be = spawn(isWin ? '.\\mvnw.cmd' : './mvnw', ['spring-boot:run'], { 
  cwd: path.join(__dirname, 'backend'), 
  stdio: 'inherit', 
  shell: true,
  env: envVars 
});

const cleanup = () => {
  if (fe.pid) fe.kill();
  if (be.pid) be.kill();
  process.exit();
};

process.on('SIGINT', cleanup);
process.on('SIGTERM', cleanup);
