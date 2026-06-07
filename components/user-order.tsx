"use client";

import Link from "next/link";
import {
    Package,
    Truck,
    CheckCircle2,
    Clock,
    XCircle,
    Send,
    Loader2,
} from "lucide-react";
import {
    Item,
    ItemContent,
    ItemDescription,
    ItemGroup,
    ItemMedia,
    ItemTitle,
    ItemFooter,
} from "@/components/ui/item";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { orderCompartmentMissing, orderNeedsCompartment, statusLabels, type Order, type OrderStatus } from "@/lib/orders";
import { dispatchRobot, fetchActiveOrders, fetchOrderHistory } from "@/lib/api";
import { toast } from "sonner";
import {
    dispatchSuccess,
    userToastError,
    userToastSuccess,
    userToastWarn,
} from "@/lib/user-toast-styles";
import { useState, useEffect } from "react";
import { useScrollRestoration } from "@/hooks/use-scroll-restoration";
import { useOrdersWebSocket } from "@/hooks/use-orders-websocket";

function getStatusIcon(status: OrderStatus) {
    switch (status) {
        case "pending":
            return <Clock className="h-3.5 w-3.5" />;
        case "placed":
            return <Package className="h-3.5 w-3.5" />;
        case "shipping":
            return <Truck className="h-3.5 w-3.5" />;
        case "delivered":
            return <CheckCircle2 className="h-3.5 w-3.5" />;
        case "cancelled":
            return <XCircle className="h-3.5 w-3.5" />;
    }
}

function getStatusBadgeStyle(status: OrderStatus) {
    switch (status) {
        case "pending":
            return "bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300";
        case "placed":
            return "bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300";
        case "shipping":
            return "bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300";
        case "delivered":
            return "bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300";
        case "cancelled":
            return "bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300";
    }
}

import { useAnimations } from "@/contexts/animation-context";

function OrderCardItem({
    order,
    index,
    animate,
    callShipLoading,
    setCallShipLoading,
}: {
    order: Order;
    index: number;
    animate: boolean;
    callShipLoading: string | null;
    setCallShipLoading: (id: string | null) => void;
}) {
    const busy = callShipLoading === order.maDonHang;

    const handleCallDelivery = async (e: React.MouseEvent) => {
        e.preventDefault();
        e.stopPropagation();
        if (orderCompartmentMissing(order)) {
            toast.error("Chưa có mã ngăn tủ. Vui lòng đợi sau khi đóng tủ.", userToastWarn);
            return;
        }
        setCallShipLoading(order.maDonHang);
        try {
            const result = await dispatchRobot(order.maDonHang, {});
            toast.success(
                dispatchSuccess(order, result.estimated_time_of_arrival),
                userToastSuccess
            );
        } catch (err) {
            toast.error(
                err instanceof Error ? err.message : "Không gọi được giao hàng",
                userToastError
            );
        } finally {
            setCallShipLoading(null);
        }
    };

    return (
        <Item
            variant="outline"
            role="listitem"
            className={`flex-col gap-0 p-0 ${animate ? 'animate-in fade-in slide-in-from-bottom-2' : ''} transition-all duration-200 hover:border-primary/70`}
            style={animate ? { animationDelay: `${index * 50}ms`, animationFillMode: "both" } : undefined}
        >
            {/* Main content — clickable link */}
            <Item
                variant="default"
                render={<Link href={`/user/orders/${order.maDonHang}`} />}
                className="w-full "
            >
                <ItemMedia variant="icon">
                    <Truck className="h-5 w-5 text-primary" />
                </ItemMedia>
                <ItemContent>
                    <ItemTitle className="line-clamp-1">
                        {order.maDonHang}
                    </ItemTitle>
                    <ItemDescription>{order.sanPham}</ItemDescription>
                    {order.pinCode && (
                        <p className="mt-1 flex w-max items-center gap-1 rounded-md bg-primary/10 px-2 py-0.5 text-xs font-mono font-medium text-primary">
                            PIN: {order.pinCode}
                        </p>
                    )}
                    {orderNeedsCompartment(order.trangThai) && (
                        <p
                            className={`mt-1 text-xs font-medium ${
                                orderCompartmentMissing(order)
                                    ? "text-amber-600 dark:text-amber-500"
                                    : "text-foreground"
                            }`}
                        >
                            {order.compartmentId != null
                                ? `Compartment ID: ${order.compartmentId}`
                                : "Compartment ID: null"}
                        </p>
                    )}
                </ItemContent>
                <ItemContent className="flex-none text-center">
                    <Badge variant="outline" className={`gap-1 border-transparent ${getStatusBadgeStyle(order.trangThai)}`}>
                        {getStatusIcon(order.trangThai)}
                        {statusLabels[order.trangThai]}
                    </Badge>
                </ItemContent>
            </Item>

            {/* Action Buttons */}
            {(order.trangThai === "placed" || order.trangThai === "shipping" || order.trangThai === "delivered") && (
                <ItemFooter className="w-full border-t border-border/50 px-3 py-2">
                    {order.trangThai === "placed" && (
                        <Button
                            className="w-full gap-2"
                            size="lg"
                            disabled={busy || orderCompartmentMissing(order)}
                            onClick={handleCallDelivery}
                        >
                            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
                            Gọi giao hàng
                        </Button>
                    )}

                    {order.trangThai === "shipping" && (
                        <Button size="lg" className="w-full" nativeButton={false} render={
                            <Link href={`/user/orders/${order.maDonHang}`} />
                        }>
                            Track
                        </Button>
                    )}

                    {order.trangThai === "delivered" && (
                        <Button variant="outline" size="lg" className="w-full" nativeButton={false} render={
                            <Link href={`/user/orders/${order.maDonHang}`} />
                        }>
                            View E-Receipt
                        </Button>
                    )}
                </ItemFooter>
            )}
        </Item>
    );
}

export default function UserOrderList() {
    useScrollRestoration();
    const { animationsEnabled } = useAnimations();
    const [orders, setOrders] = useState<Order[]>([]);
    const [loading, setLoading] = useState(true);
    const [callShipLoading, setCallShipLoading] = useState<string | null>(null);

    // Initial fetch
    useEffect(() => {
        async function loadData() {
            setLoading(true);
            try {
                const data = await fetchActiveOrders();
                setOrders(data);
            } catch (err) {
                console.warn("UserOrder fetch error:", err instanceof Error ? err.message : String(err));
            } finally {
                setLoading(false);
            }
        }
        loadData();
    }, []);

    // Real-time updates via WebSocket
    useOrdersWebSocket((updatedOrders) => {
        setOrders(updatedOrders);
        setLoading(false);
    });

    return (
        <div className="mx-auto w-full max-w-md px-4 pb-6">
            <ItemGroup className="gap-3">
                {loading ? (
                    <>
                        {Array.from({ length: 3 }).map((_, i) => (
                            <div key={i} className={`flex flex-col gap-0 rounded-xl border border-border/50 p-0 ${animationsEnabled ? 'animate-pulse' : ''}`}>
                                <div className="flex items-center gap-3 p-3.5">
                                    <div className="h-10 w-10 rounded-xl bg-muted" />
                                    <div className="flex-1 space-y-2">
                                        <div className="h-4 w-2/3 rounded-lg bg-muted" />
                                        <div className="h-3 w-1/3 rounded-lg bg-muted" />
                                    </div>
                                    <div className="h-6 w-16 rounded-full bg-muted" />
                                </div>
                            </div>
                        ))}
                    </>
                ) : orders.length === 0 ? (
                    <div className="flex flex-col items-center gap-3 py-16 text-muted-foreground">
                        <Package className="h-12 w-12 opacity-30" />
                        <p className="text-sm">No orders found</p>
                    </div>
                ) : (
                    orders.map((order, idx) => (
                        <OrderCardItem
                            key={order.maDonHang}
                            order={order}
                            index={idx}
                            animate={animationsEnabled}
                            callShipLoading={callShipLoading}
                            setCallShipLoading={setCallShipLoading}
                        />
                    ))
                )}
            </ItemGroup>
        </div>
    );
}
