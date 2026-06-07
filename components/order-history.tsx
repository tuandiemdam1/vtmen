"use client";

import Link from "next/link";
import {
    Item,
    ItemContent,
    ItemDescription,
    ItemGroup,
    ItemMedia,
    ItemTitle,
} from "@/components/ui/item";
import { Badge } from "@/components/ui/badge";
import { Package, CheckCircle2, History, ArrowLeft, XCircle } from "lucide-react";

import { statusLabels, type OrderStatus, type Order } from "@/lib/orders";
import { useState, useEffect } from "react";
import { fetchOrderHistory } from "@/lib/api";
import { useScrollRestoration } from "@/hooks/use-scroll-restoration";

import { useAnimations } from "@/contexts/animation-context";

const statusStyles: Record<OrderStatus, string> = {
    pending: "bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300",
    placed: "bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300",
    shipping: "bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300",
    delivered: "bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300",
    cancelled: "bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300",
};

const statusIcons: Record<OrderStatus, React.ReactNode> = {
    pending: <Package className="h-3.5 w-3.5" />,
    placed: <Package className="h-3.5 w-3.5" />,
    shipping: <Package className="h-3.5 w-3.5" />,
    delivered: <CheckCircle2 className="h-3.5 w-3.5" />,
    cancelled: <XCircle className="h-3.5 w-3.5" />,
};

export default function OrderHistory() {
    useScrollRestoration();
    const { animationsEnabled } = useAnimations();
    const [orders, setOrders] = useState<Order[]>([]);
    const [loading, setLoading] = useState(true);
    const [justAddedIds, setJustAddedIds] = useState<string[]>([]);

    useEffect(() => {
        let isMounted = true;

        const loadData = async () => {
            try {
                const data = await fetchOrderHistory();
                if (!isMounted) return;

                setOrders((prev) => {
                    // Detect new orders that just entered history (delivered/cancelled)
                    const prevIds = new Set(prev.map((o) => o.maDonHang));
                    const newOnes = data.filter((o) => !prevIds.has(o.maDonHang));

                    if (newOnes.length > 0) {
                        setJustAddedIds((ids) => [
                            ...ids,
                            ...newOnes.map((o) => o.maDonHang),
                        ]);
                    }

                    return data;
                });
            } catch (err) {
                console.warn("OrderHistory fetch error:", err instanceof Error ? err.message : String(err));
            } finally {
                if (isMounted) {
                    setLoading(false);
                }
            }
        };

        // Initial load
        loadData();

        // Poll periodically to catch newly completed/cancelled orders
        const interval = setInterval(loadData, 5000);

        return () => {
            isMounted = false;
            clearInterval(interval);
        };
    }, []);

    return (
        <div className="mx-auto flex w-full max-w-md flex-col gap-6 bg-background pt-3 pb-6">
            <ItemGroup className="gap-4 px-4">
                {loading ? (
                    <>
                        {Array.from({ length: 3 }).map((_, i) => (
                            <div key={i} className={`flex items-center gap-3 rounded-xl border border-border/50 p-3.5 ${animationsEnabled ? 'animate-pulse' : ''}`}>
                                <div className="h-10 w-10 rounded-xl bg-muted" />
                                <div className="flex-1 space-y-2">
                                    <div className="h-4 w-2/3 rounded-lg bg-muted" />
                                    <div className="h-3 w-1/3 rounded-lg bg-muted" />
                                </div>
                                <div className="h-6 w-16 rounded-full bg-muted" />
                            </div>
                        ))}
                    </>
                ) : orders.length === 0 ? (
                    <div className="flex flex-col items-center gap-3 py-16 text-muted-foreground">
                        <History className="h-12 w-12 opacity-30" />
                        <p className="text-sm">Không có lịch sử đơn hàng</p>
                    </div>
                ) : (
                    orders.map((order, idx) => (
                        <Item
                            key={order.maDonHang}
                            variant="outline"
                            role="listitem"
                            render={<Link href={`/postman/orders/${order.maDonHang}`} />}
                            className={`${animationsEnabled ? 'animate-in fade-in slide-in-from-bottom-2' : ''} ${
                                justAddedIds.includes(order.maDonHang)
                                    ? 'animate-in fade-in slide-in-from-bottom-2'
                                    : ''
                            } transition-all duration-200 hover:border-primary/70`}
                            style={
                                animationsEnabled
                                    ? {
                                          animationDelay: `${idx * 50}ms`,
                                          animationFillMode: "both",
                                      }
                                    : undefined
                            }
                        >
                            <ItemMedia variant="icon">
                                {order.trangThai === "delivered" ? (
                                    <CheckCircle2 className="h-5 w-5 text-green-600" />
                                ) : (
                                    <XCircle className="h-5 w-5 text-red-600" />
                                )}
                            </ItemMedia>
                            <ItemContent>
                                <ItemTitle className="line-clamp-1">
                                    {order.maDonHang} -{" "}
                                    <span className="text-muted-foreground">{order.tenKhachHang}</span>
                                </ItemTitle>
                                <ItemDescription>{order.sanPham}</ItemDescription>
                            </ItemContent>
                            <ItemContent className="flex-none text-center">
                                <Badge variant="outline" className={`gap-1 border-transparent ${statusStyles[order.trangThai]}`}>
                                    {statusIcons[order.trangThai]}
                                    {statusLabels[order.trangThai] || "Hoàn thành"}
                                </Badge>
                            </ItemContent>
                        </Item>
                    ))
                )}
            </ItemGroup>
        </div>
    );
}
