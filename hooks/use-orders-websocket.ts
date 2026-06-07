"use client";

import { useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { BackendOrder, mapBackendOrderToFrontend } from "@/lib/api";
import { Order } from "@/lib/orders";

// Nếu có biến môi trường thì dùng, nếu không thì tự động kiểm tra xem trang web đang chạy https hay http
const WS_BASE = process.env.NEXT_PUBLIC_WS_URL || 
  (typeof window !== 'undefined' && window.location.protocol === 'https:' 
    ? "https://vtmen-production.up.railway.app" 
    : "http://localhost:8080");

export function useOrdersWebSocket(onUpdate: (orders: Order[]) => void) {
    const [connected, setConnected] = useState(false);
    const onUpdateRef = useRef(onUpdate);

    useEffect(() => {
        onUpdateRef.current = onUpdate;
    }, [onUpdate]);

    useEffect(() => {
        const client = new Client({
            webSocketFactory: () => new SockJS(`${WS_BASE}/ws`),
            reconnectDelay: 5000,
            onConnect: () => {
                setConnected(true);
                console.log("[WebSocket] Connected to", `${WS_BASE}/ws`);
                client.subscribe("/topic/orders", (message) => {
                    console.log("[WebSocket] Orders update received");
                    const raw: BackendOrder[] = JSON.parse(message.body);
                    onUpdateRef.current(raw.map(mapBackendOrderToFrontend));
                });
            },
            onDisconnect: () => {
                setConnected(false);
                console.warn("[WebSocket] Disconnected — will retry in 5s");
            },
            onStompError: (frame) => {
                console.error("[WebSocket] STOMP error:", frame.headers["message"]);
            },
        });

        client.activate();

        return () => {
            client.deactivate();
        };
    }, []);

    return { connected };
}
