"use client";

import Link from "next/link"
import {
    Item,
    ItemContent,
    ItemDescription,
    ItemGroup,
    ItemMedia,
    ItemTitle,
} from "@/components/ui/item"
import { Badge } from "@/components/ui/badge"
import { Truck, Clock, Package, CheckCircle2, XCircle } from "lucide-react"

import { orderCompartmentMissing, orderNeedsCompartment, statusLabels, type OrderStatus, type Order } from "@/lib/orders"
import { useState, useEffect } from "react"
import { fetchActiveOrders } from "@/lib/api"
import { useScrollRestoration } from "@/hooks/use-scroll-restoration";
import { useAnimations } from "@/contexts/animation-context";
import { useOrdersWebSocket } from "@/hooks/use-orders-websocket";

const statusStyles: Record<OrderStatus, string> = {
    pending: "bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300",
    placed: "bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300",
    shipping: "bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300",
    delivered: "bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300",
    cancelled: "bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300",
}

const statusIcons: Record<OrderStatus, React.ReactNode> = {
    pending: <Clock className="h-3.5 w-3.5" />,
    placed: <Package className="h-3.5 w-3.5" />,
    shipping: <Truck className="h-3.5 w-3.5" />,
    delivered: <CheckCircle2 className="h-3.5 w-3.5" />,
    cancelled: <XCircle className="h-3.5 w-3.5" />,
}

export default function OrderCard({ onDataChange }: { onDataChange?: () => void }) {
    useScrollRestoration();
    const { animationsEnabled } = useAnimations();
    const [orders, setOrders] = useState<Order[]>([]);
    const [loading, setLoading] = useState(true);

    // Initial fetch
    useEffect(() => {
        async function loadData() {
            setLoading(true);
            try {
                const data = await fetchActiveOrders();
                setOrders(data);
            } catch (err) {
                console.warn("loadData error:", err instanceof Error ? err.message : String(err));
            } finally {
                setLoading(false);
            }
        }
        loadData();
    }, []);

    // Real-time updates via WebSocket
    useOrdersWebSocket((updatedOrders) => {
        setOrders((prev) => {
            const statusChanged =
                prev.length > 0 &&
                prev.some((prevOrder) => {
                    const next = updatedOrders.find(
                        (o) => o.maDonHang === prevOrder.maDonHang
                    );
                    return next && next.trangThai !== prevOrder.trangThai;
                });

            if (statusChanged && onDataChange) {
                setTimeout(() => onDataChange(), 0);
            }

            return updatedOrders;
        });
        setLoading(false);
    });

    return (
        <div className="flex w-full max-w-md flex-col gap-6 px-4">
            <ItemGroup className="gap-4">
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
                        <Package className="h-12 w-12 opacity-30" />
                        <p className="text-sm">Không có đơn hàng nào</p>
                    </div>
                ) : (
                    orders.map((order, idx) => (
                        <Item
                            key={order.maDonHang}
                            variant="outline"
                            role="listitem"
                            render={<Link href={`orders/${order.maDonHang}`} />}
                            className={`${animationsEnabled ? 'animate-in fade-in slide-in-from-bottom-2' : ''} transition-all duration-200 hover:border-primary/70`}
                            style={animationsEnabled ? { animationDelay: `${idx * 50}ms`, animationFillMode: "both" } : undefined}
                        >
                            <ItemMedia variant="icon">
                                <Truck className="h-5 w-5 text-primary" />
                            </ItemMedia>
                            <ItemContent>
                                <ItemTitle className="line-clamp-1">
                                    {order.maDonHang} -{" "}
                                    <span className="text-muted-foreground">{order.tenKhachHang}</span>
                                </ItemTitle>
                                <ItemDescription>{order.sanPham}</ItemDescription>
                                {order.pinCode && (
                                    <p className="mt-1 flex w-max items-center gap-1 rounded-md bg-primary/10 px-2 py-0.5 text-xs font-mono font-medium text-primary">
                                        PIN: {order.pinCode}
                                    </p>
                                )}
                                {orderNeedsCompartment(order.trangThai) && (
                                    <p
                                        className={`mt-0.5 text-[11px] font-medium ${
                                            orderCompartmentMissing(order)
                                                ? "text-amber-600 dark:text-amber-500"
                                                : "text-muted-foreground"
                                        }`}
                                    >
                                        Compartment ID:{" "}
                                        {order.compartmentId != null ? order.compartmentId : "null"}
                                    </p>
                                )}
                            </ItemContent>
                            <ItemContent className="flex-none text-center">
                                <Badge variant="outline" className={`gap-1 border-transparent ${statusStyles[order.trangThai]}`}>
                                    {statusIcons[order.trangThai]}
                                    {statusLabels[order.trangThai]}
                                </Badge>
                            </ItemContent>
                        </Item>
                    ))
                )}
            </ItemGroup>
        </div>
    )
}
