"use client";

import { orderCompartmentMissing, orderNeedsCompartment, type Order } from "@/lib/orders";
import { fetchActiveOrders, fetchOrderHistory, cancelOrder, cancelDelivery, confirmOrderPlaced } from "@/lib/api";
import { useRouter } from "next/navigation";
import { ArrowLeft, AlertTriangle, Loader2 } from "lucide-react";
import { useState, useEffect } from "react";
import { useSwipeBack } from "@/hooks/use-swipe-back";

import { useAnimations } from "@/contexts/animation-context";
import { useOrdersWebSocket } from "@/hooks/use-orders-websocket";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogMedia,
    AlertDialogTitle,
    AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import UpdateOrderDrawer from "@/components/update-order-drawer";

const statusStyles: Record<string, string> = {
    pending: "bg-yellow-100 text-yellow-800",
    placed: "bg-orange-100 text-orange-800",
    shipping: "bg-blue-100 text-blue-800",
    delivered: "bg-green-100 text-green-800",
    cancelled: "bg-red-100 text-red-800",
};

function InfoRow({ label, value }: { label: string; value: string }) {
    return (
        <div className="flex justify-between py-3 border-b border-border last:border-b-0">
            <span className="text-muted-foreground text-sm">{label}</span>
            <span className="text-sm font-medium text-foreground text-right max-w-[60%]">{value}</span>
        </div>
    );
}

export default function OrderDetail({ orderId }: { orderId: string }) {
    const router = useRouter();
    useSwipeBack('/postman/orders');
    const { animationsEnabled } = useAnimations();
    const [order, setOrder] = useState<Order | null>(null);
    const [loading, setLoading] = useState(true);
    const [updateDrawerOpen, setUpdateDrawerOpen] = useState(false);
    const [cancelLoading, setCancelLoading] = useState(false);
    const [confirmLoading, setConfirmLoading] = useState(false);
    const [selectedCompartment, setSelectedCompartment] = useState<string>("1");

    const loadOrder = async () => {
        setLoading(true);
        try {
            if (animationsEnabled) {
                await new Promise((resolve) => setTimeout(resolve, 300));
            }
            const [active, history] = await Promise.all([
                fetchActiveOrders(),
                fetchOrderHistory()
            ]);
            const allOrders = [...active, ...history];
            const found = allOrders.find((o) => o.maDonHang === orderId);
            if (found) {
                setOrder(found);
            }
        } catch (err) {
            console.warn("OrderDetail fetch error:", err instanceof Error ? err.message : String(err));
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadOrder();
    }, [orderId, animationsEnabled]);

    // Instant update when this order changes via WebSocket
    useOrdersWebSocket((updatedOrders) => {
        const found = updatedOrders.find((o) => o.maDonHang === orderId);
        if (found) {
            setOrder(found);
        }
    });

    const handleCancelOrder = async () => {
        if (!order) return;
        setCancelLoading(true);
        try {
            const success = await cancelOrder(order.maDonHang);
            if (success) {
                toast.success(`Đã hủy đơn hàng ${order.maDonHang}`, {
                    className: "!text-red-500 !border-red-600",
                });
                // Navigate back after cancellation
                if (window.history.length > 2) {
                    router.back();
                } else {
                    router.replace('/postman/orders');
                }
            } else {
                toast.error("Không thể hủy đơn hàng. Vui lòng thử lại sau.");
            }
        } catch (error) {
            toast.error("Đã xảy ra lỗi khi hủy đơn hàng");
        } finally {
            setCancelLoading(false);
        }
    };

    const handleCancelDelivery = async () => {
        if (!order) return;
        setCancelLoading(true);
        try {
            const success = await cancelDelivery(order.maDonHang);
            if (success) {
                toast.success(`Đã hủy giao hàng ${order.maDonHang} và gọi robot về sạc`, {
                    className: "!text-amber-500 !border-amber-600",
                });
                // Navigate back after cancellation
                if (window.history.length > 2) {
                    router.back();
                } else {
                    router.replace('/postman/orders');
                }
            } else {
                toast.error("Không thể hủy giao hàng. Vui lòng thử lại sau.");
            }
        } catch (error) {
            toast.error("Đã xảy ra lỗi khi hủy giao hàng");
        } finally {
            setCancelLoading(false);
        }
    };

    const handleOrderUpdated = () => {
        // Re-fetch to show updated data
        loadOrder();
    };

    const handleConfirmPlaced = async () => {
        if (!order) return;
        const compId = parseInt(selectedCompartment, 10);
        if (isNaN(compId)) {
            toast.error("Vui lòng chọn ngăn tủ hợp lệ");
            return;
        }
        setConfirmLoading(true);
        try {
            const success = await confirmOrderPlaced(order.maDonHang, compId);
            if (success) {
                toast.success(`Đã xác nhận giao hàng cho đơn ${order.maDonHang}`, {
                    className: "!text-green-600 !border-green-600",
                });
                loadOrder();
            } else {
                toast.error("Không thể xác nhận giao hàng. Vui lòng thử lại sau.");
            }
        } catch (error) {
            toast.error("Đã xảy ra lỗi khi xác nhận giao hàng");
        } finally {
            setConfirmLoading(false);
        }
    };

    return (
        <div className={`flex-1 px-4 pt-4 pb-6 ${animationsEnabled ? 'animate-in slide-in-from-right fade-in duration-300 fill-mode-both' : ''}`}>
            {loading ? (
                <div className={`flex h-[60vh] items-center justify-center text-muted-foreground ${animationsEnabled ? 'animate-pulse' : ''}`}>
                    Đang tải dữ liệu...
                </div>
            ) : !order ? (
                <div className="flex h-[60vh] flex-col items-center justify-center gap-4 text-center">
                    <h2 className="text-lg font-semibold text-foreground">Không tìm thấy đơn hàng</h2>
                    <button
                        className="rounded-lg bg-primary px-6 py-2.5 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
                        onClick={() => {
                            if (window.history.length > 2) {
                                router.back();
                            } else {
                                router.replace('/postman/orders');
                            }
                        }}
                    >
                        Quay lại
                    </button>
                </div>
            ) : (
                <div className={`space-y-5 ${animationsEnabled ? 'animate-in fade-in duration-500' : ''}`}>
                    {/* Header */}
                    <div className={`flex flex-col gap-4 ${animationsEnabled ? 'animate-in fade-in slide-in-from-bottom-2' : ''}`} style={animationsEnabled ? { animationDelay: "50ms", animationFillMode: "both" } : undefined}>
                        <div className="flex items-center justify-between">
                            <div className="flex items-center gap-3">
                                <button
                                    onClick={() => {
                                        if (window.history.length > 2) {
                                            router.back();
                                        } else {
                                            router.replace('/postman/orders');
                                        }
                                    }}
                                    className="flex h-9 w-9 items-center justify-center rounded-lg border border-border bg-card transition-colors hover:border-primary hover:text-primary"
                                    aria-label="Go back"
                                >
                                    <ArrowLeft className="h-4 w-4" />
                                </button>
                                <h1 className="text-lg font-bold text-foreground">
                                    Order &quot;<span className="text-primary">{order.maDonHang}</span>&quot;
                                </h1>
                            </div>
                            <span
                                className={`inline-block rounded-full px-3 py-1 text-xs font-semibold capitalize ${statusStyles[order.trangThai] ?? "bg-muted text-muted-foreground"}`}
                            >
                                {order.trangThai}
                            </span>
                        </div>
                    </div>

                    {/* PIN Code */}
                    <div className={`flex justify-center rounded-xl border border-border bg-white p-6 shadow-sm ${animationsEnabled ? 'animate-in fade-in zoom-in-95' : ''}`} style={animationsEnabled ? { animationDelay: "150ms", animationFillMode: "both" } : undefined}>
                        <div className="text-center">
                            <h3 className="mb-2 text-sm font-medium text-muted-foreground">Mã Nhận Hàng</h3>
                            <div className="rounded-lg bg-primary/10 px-6 py-3">
                                <span className="text-3xl font-mono font-bold tracking-widest text-primary">
                                    {order.pinCode || "------"}
                                </span>
                            </div>
                        </div>
                    </div>

                    {/* Order Info */}
                    <div className={`rounded-xl border border-border bg-card p-4 shadow-sm ${animationsEnabled ? 'animate-in fade-in slide-in-from-bottom-3' : ''}`} style={animationsEnabled ? { animationDelay: "250ms", animationFillMode: "both" } : undefined}>
                        <h2 className="mb-2 text-base font-semibold text-foreground">
                            Order Information
                        </h2>
                        <InfoRow label="Order ID" value={order.maDonHang} />
                        <InfoRow label="Product" value={order.sanPham} />
                        <InfoRow label="Quantity" value={order.soLuong?.toString() || "1"} />
                        <InfoRow label="Customer" value={order.tenKhachHang} />
                        <InfoRow label="Phone" value={order.sdt} />
                        <InfoRow label="Address" value={order.diaChi} />
                        {orderNeedsCompartment(order.trangThai) && (
                            <InfoRow
                                label="Compartment ID"
                                value={
                                    order.compartmentId != null
                                        ? String(order.compartmentId)
                                        : "null"
                                }
                            />
                        )}
                    </div>
                    {orderNeedsCompartment(order.trangThai) && orderCompartmentMissing(order) && (
                        <div className="rounded-lg border border-amber-500/50 bg-amber-500/10 px-3 py-2 text-xs text-amber-800 dark:text-amber-200">
                            <span className="font-mono">compartment_id</span> is not set yet. Assign it via{" "}
                            <span className="font-mono">POST /api/dcs/deposit-closed</span> (that call does not change
                            workflow status).
                        </div>
                    )}

                    {/* Action Buttons */}
                    <div className={order.trangThai === "cancelled" || order.trangThai === "delivered" ? "hidden" : `flex flex-wrap gap-2 items-center justify-end ${animationsEnabled ? 'animate-in slide-in-from-bottom-4 duration-200' : ''}`} style={order.trangThai === "pending" && animationsEnabled ? { animationDelay: "350ms", animationFillMode: "both" } : undefined}>
                        {/* Confirm Placed Button with AlertDialog */}
                        {order.trangThai === "pending" && (
                            <AlertDialog>
                                <AlertDialogTrigger
                                    render={
                                        <Button
                                            variant="default"
                                            size="lg"
                                            className="bg-green-600 hover:bg-green-700 text-white"
                                            disabled={confirmLoading}
                                        >
                                            {confirmLoading ? (
                                                <>
                                                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                                    Đang xử lý...
                                                </>
                                            ) : (
                                                "Xác nhận gửi hàng"
                                            )}
                                        </Button>
                                    }
                                />
                                <AlertDialogContent size="sm">
                                    <AlertDialogHeader>
                                        <AlertDialogTitle>Xác nhận gửi hàng</AlertDialogTitle>
                                        <AlertDialogDescription render={<div />}>
                                            <div>
                                                <p>Vui lòng chọn ngăn tủ để bỏ hàng vào cho đơn <span className="font-semibold text-foreground">{order.maDonHang}</span>.</p>
                                                <div className="mt-4 text-left">
                                                    <label className="block text-sm font-medium mb-1 text-foreground">Ngăn tủ số</label>
                                                    <select 
                                                        className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                                                        value={selectedCompartment}
                                                        onChange={(e) => setSelectedCompartment(e.target.value)}
                                                    >
                                                        <option value="1">Ngăn tủ 1</option>
                                                        <option value="2">Ngăn tủ 2</option>
                                                        <option value="3">Ngăn tủ 3</option>
                                                        <option value="4">Ngăn tủ 4</option>
                                                    </select>
                                                </div>
                                            </div>
                                        </AlertDialogDescription>
                                    </AlertDialogHeader>
                                    <AlertDialogFooter>
                                        <AlertDialogCancel>Hủy</AlertDialogCancel>
                                        <AlertDialogAction
                                            onClick={handleConfirmPlaced}
                                            disabled={confirmLoading}
                                            className="bg-green-600 hover:bg-green-700 text-white"
                                        >
                                            Xác nhận
                                        </AlertDialogAction>
                                    </AlertDialogFooter>
                                </AlertDialogContent>
                            </AlertDialog>
                        )}
                        {/* Update Order Button */}
                        <Button
                            variant="default"
                            size="lg"
                            className="bg-blue-500 hover:bg-blue-700"
                            onClick={() => setUpdateDrawerOpen(true)}
                        >
                            Update Order
                        </Button>

                        {/* Cancel Order Button with AlertDialog */}
                        <AlertDialog>
                            <AlertDialogTrigger
                                render={
                                    <Button
                                        variant="outline"
                                        size="lg"
                                        className="border-red-300 text-red-600 hover:bg-red-50 hover:text-red-700"
                                    >
                                        Cancel Order
                                    </Button>
                                }
                            />
                            <AlertDialogContent size="sm">
                                <AlertDialogHeader>
                                    <AlertDialogMedia className="bg-red-100 text-red-600">
                                        <AlertTriangle className="h-5 w-5" />
                                    </AlertDialogMedia>
                                    <AlertDialogTitle>Hủy đơn hàng?</AlertDialogTitle>
                                    <AlertDialogDescription>
                                        Bạn có chắc chắn muốn hủy đơn hàng <span className="font-semibold text-foreground">{order.maDonHang}</span>? Hành động này không thể hoàn tác.
                                    </AlertDialogDescription>
                                </AlertDialogHeader>
                                <AlertDialogFooter>
                                    <AlertDialogCancel>Quay lại</AlertDialogCancel>
                                    <AlertDialogAction
                                        onClick={handleCancelOrder}
                                        disabled={cancelLoading}
                                        className="bg-red-600 hover:bg-red-700"
                                    >
                                        {cancelLoading ? (
                                            <>
                                                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                                Đang hủy...
                                            </>
                                        ) : (
                                            "Xác nhận hủy"
                                        )}
                                    </AlertDialogAction>
                                </AlertDialogFooter>
                            </AlertDialogContent>
                        </AlertDialog>

                        {/* Cancel Delivery Button with AlertDialog */}
                        {order.trangThai === "shipping" && (
                            <AlertDialog>
                                <AlertDialogTrigger
                                    render={
                                        <Button
                                            variant="outline"
                                            size="lg"
                                            className="border-amber-300 text-amber-600 hover:bg-amber-50 hover:text-amber-700"
                                        >
                                            Hủy giao hàng
                                        </Button>
                                    }
                                />
                                <AlertDialogContent size="sm">
                                    <AlertDialogHeader>
                                        <AlertDialogMedia className="bg-amber-100 text-amber-600">
                                            <AlertTriangle className="h-5 w-5" />
                                        </AlertDialogMedia>
                                        <AlertDialogTitle>Hủy giao hàng?</AlertDialogTitle>
                                        <AlertDialogDescription>
                                            Bạn có chắc chắn muốn hủy giao hàng cho đơn <span className="font-semibold text-foreground">{order.maDonHang}</span>? Robot sẽ hủy nhiệm vụ hiện tại và tự động quay về điểm sạc.
                                        </AlertDialogDescription>
                                    </AlertDialogHeader>
                                    <AlertDialogFooter>
                                        <AlertDialogCancel>Quay lại</AlertDialogCancel>
                                        <AlertDialogAction
                                            onClick={handleCancelDelivery}
                                            disabled={cancelLoading}
                                            className="bg-amber-600 hover:bg-amber-700"
                                        >
                                            {cancelLoading ? (
                                                <>
                                                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                                    Đang xử lý...
                                                </>
                                            ) : (
                                                "Xác nhận hủy"
                                            )}
                                        </AlertDialogAction>
                                    </AlertDialogFooter>
                                </AlertDialogContent>
                            </AlertDialog>
                        )}
                    </div>

                    {/* Update Order Drawer */}
                    <UpdateOrderDrawer
                        order={order}
                        open={updateDrawerOpen}
                        onOpenChange={setUpdateDrawerOpen}
                        onUpdated={handleOrderUpdated}
                    />
                </div>
            )}
        </div>
    );
}
