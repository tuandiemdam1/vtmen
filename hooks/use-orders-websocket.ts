"use client";

import { useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import { BackendOrder, mapBackendOrderToFrontend, fetchOrderByCode } from "@/lib/api";
import { Order } from "@/lib/orders";

export function getWsBase(): string {
    const ngrokDomain = process.env.NEXT_PUBLIC_NGROK_DOMAIN?.trim() || "cabalistic-grisly-kolby.ngrok-free.dev";
    const defaultNgrokWs = `https://${ngrokDomain.replace(/^https?:\/\//, "").replace(/\/+$/, "")}`;

    if (typeof window !== "undefined") {
        const { protocol, hostname } = window.location;
        const isLocalhost = hostname === "localhost" || hostname === "127.0.0.1";

        if (protocol === "https:") {
            const envWs = process.env.NEXT_PUBLIC_WS_URL?.trim();
            if (envWs && !envWs.includes("localhost") && !envWs.includes("127.0.0.1") && !envWs.includes("${")) {
                return envWs.replace(/\/+$/, "");
            }
            return defaultNgrokWs;
        }

        if (!isLocalhost) {
            const envWs = process.env.NEXT_PUBLIC_WS_URL?.trim();
            if (envWs && !envWs.includes("localhost") && !envWs.includes("127.0.0.1") && !envWs.includes("${")) {
                return envWs.replace(/\/+$/, "");
            }
            const bePort = process.env.NEXT_PUBLIC_BACKEND_PORT?.trim() || "8085";
            return `http://${hostname}:${bePort}`;
        }
    }

    const envWs = process.env.NEXT_PUBLIC_WS_URL?.trim();
    if (envWs && !envWs.includes("${")) {
        return envWs.replace(/\/+$/, "");
    }
    const envApi = process.env.NEXT_PUBLIC_API_URL?.trim();
    if (envApi && !envApi.includes("${")) {
        return envApi.replace(/\/api\/?$/, "").replace(/\/+$/, "");
    }
    return "http://localhost:8085";
}

export function getBrokerUrl(): string {
    const wsBase = getWsBase();
    const proto = wsBase.startsWith("https") ? "wss" : "ws";
    const hostAndPath = wsBase.replace(/^https?:\/\//, "");
    return `${proto}://${hostAndPath}/ws`;
}

export function useOrdersWebSocket(onUpdate: (orders: Order[]) => void) {
    const [connected, setConnected] = useState(false);
    const onUpdateRef = useRef(onUpdate);

    useEffect(() => {
        onUpdateRef.current = onUpdate;
    }, [onUpdate]);

    useEffect(() => {
        const brokerURL = getBrokerUrl();
        const client = new Client({
            brokerURL,
            reconnectDelay: 5000,
            onConnect: () => {
                setConnected(true);
                console.log("[WebSocket] Connected to", brokerURL);
                client.subscribe("/topic/orders", (message) => {
                    try {
                        const raw: BackendOrder[] = JSON.parse(message.body);
                        onUpdateRef.current(raw.map(mapBackendOrderToFrontend));
                    } catch (e) {
                        console.error("[WebSocket] Parse active orders error:", e);
                    }
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

export function useOrderDetailWebSocket(orderCode: string, onUpdate: (order: Order) => void) {
    const [connected, setConnected] = useState(false);
    const onUpdateRef = useRef(onUpdate);

    useEffect(() => {
        onUpdateRef.current = onUpdate;
    }, [onUpdate]);

    useEffect(() => {
        if (!orderCode) return;
        const brokerURL = getBrokerUrl();
        const client = new Client({
            brokerURL,
            reconnectDelay: 5000,
            onConnect: () => {
                setConnected(true);
                console.log(`[WebSocket] Connected for order ${orderCode} at ${brokerURL}`);
                
                // 1. Direct subscription to specific order topic
                client.subscribe(`/topic/orders/${orderCode}`, (message) => {
                    try {
                        console.log(`[WebSocket] Direct update received for order ${orderCode}`);
                        const raw: BackendOrder = JSON.parse(message.body);
                        onUpdateRef.current(mapBackendOrderToFrontend(raw));
                    } catch (e) {
                        console.error("[WebSocket] Parse direct order error:", e);
                    }
                });

                // 2. Active orders topic (fallback check)
                client.subscribe("/topic/orders", async (message) => {
                    try {
                        const rawList: BackendOrder[] = JSON.parse(message.body);
                        const found = rawList.find(o => o.orderCode === orderCode || o.id === orderCode);
                        if (found) {
                            onUpdateRef.current(mapBackendOrderToFrontend(found));
                        } else {
                            // Order is no longer in active orders (e.g. moved to delivered or cancelled via API)
                            console.log(`[WebSocket] Order ${orderCode} not in active orders, fetching status...`);
                            const fresh = await fetchOrderByCode(orderCode);
                            if (fresh) {
                                onUpdateRef.current(fresh);
                            }
                        }
                    } catch (e) {
                        console.error("[WebSocket] Process active orders error:", e);
                    }
                });
            },
            onDisconnect: () => {
                setConnected(false);
            },
            onStompError: (frame) => {
                console.error("[WebSocket] STOMP error:", frame.headers["message"]);
            },
        });

        client.activate();

        return () => {
            client.deactivate();
        };
    }, [orderCode]);

    return { connected };
}
