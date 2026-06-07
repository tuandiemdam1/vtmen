"use client";

import { useRouter } from "next/navigation";
import { ArrowLeft, MessageCircle, Phone, Package, Clock, CheckCircle2, Truck, Send, Box, Loader2, QrCode } from "lucide-react";
import { orderCompartmentMissing, orderNeedsCompartment, type Order } from "@/lib/orders";
import { dispatchRobot, fetchActiveOrders, fetchOrderHistory, completeOrderApi } from "@/lib/api";
import { useOrdersWebSocket } from "@/hooks/use-orders-websocket";
import { toast } from "sonner";
import {
    dispatchSuccess,
    userToastError,
    userToastSuccess,
    userToastWarn,
} from "@/lib/user-toast-styles";
import { useState, useEffect } from "react";
import { useSwipeBack } from "@/hooks/use-swipe-back";

import { useAnimations } from "@/contexts/animation-context";

const trackingSteps = [
    { key: "pending", label: "Đơn hàng đang chờ", icon: Clock },
    { key: "placed", label: "Đã gửi vào tủ", icon: Package },
    { key: "shipping", label: "Robot đang giao hàng", icon: Truck },
    { key: "delivered", label: "Giao hàng thành công", icon: CheckCircle2 },
];

function getStepStatus(orderStatus: string, stepKey: string) {
    const statusOrder = ["pending", "placed", "shipping", "delivered"];
    const orderIdx = statusOrder.indexOf(orderStatus);
    const stepIdx = statusOrder.indexOf(stepKey);
    if (stepIdx < orderIdx) return "completed";
    if (stepIdx === orderIdx) return "current";
    return "waiting";
}

export default function UserOrderDetail({ orderId }: { orderId: string }) {
    const router = useRouter();
    useSwipeBack('/user/orders');
    const { animationsEnabled } = useAnimations();
    const [order, setOrder] = useState<Order | null>(null);
    const [loading, setLoading] = useState(true);
    const [callShipLoading, setCallShipLoading] = useState(false);

    useEffect(() => {
        async function loadOrder() {
            setLoading(true);
            try {
                // Wait for the slide animation to finish (300ms) before fetching
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
                console.warn("UserOrderDetail fetch error:", err instanceof Error ? err.message : String(err));
            } finally {
                setLoading(false);
            }
        }
        loadOrder();
    }, [orderId, animationsEnabled]);

    useOrdersWebSocket((updatedOrders) => {
        const found = updatedOrders.find((o) => o.maDonHang === orderId);
        if (found) setOrder(found);
    });

    const handleCallDelivery = async () => {
        if (!order) return;
        if (orderCompartmentMissing(order)) {
            toast.error(
                "Chưa có mã ngăn tủ. Vui lòng đợi hệ thống xác nhận sau khi đóng tủ.",
                userToastWarn
            );
            return;
        }
        setCallShipLoading(true);
        try {
            const result = await dispatchRobot(order.maDonHang, {});
            toast.success(
                dispatchSuccess(order, result.estimated_time_of_arrival),
                userToastSuccess
            );
        } catch (e) {
            toast.error(
                e instanceof Error ? e.message : "Không gọi được giao hàng",
                userToastError
            );
        } finally {
            setCallShipLoading(false);
        }
    };

    const handleConfirmTakeOut = async () => {
        if (!order) return;
        setCallShipLoading(true);
        try {
            const success = await completeOrderApi(order.maDonHang);
            if (success) {
                toast.success("Đã xác nhận lấy hàng thành công", userToastSuccess);
                setOrder((prev) => prev ? { ...prev, trangThai: "delivered" } : null);
            } else {
                toast.error("Không thể xác nhận lấy hàng", userToastError);
            }
        } catch (e) {
            toast.error(e instanceof Error ? e.message : "Đã xảy ra lỗi", userToastError);
        } finally {
            setCallShipLoading(false);
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
                    <Package className="h-16 w-16 text-muted-foreground/30" />
                    <h2 className="text-lg font-semibold text-foreground">Không tìm thấy đơn hàng</h2>
                    <p className="text-sm text-muted-foreground">Mã đơn: {orderId}</p>
                    <button
                        className="rounded-lg bg-primary px-6 py-2.5 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
                        onClick={() => {
                            if (window.history.length > 2) {
                                router.back();
                            } else {
                                router.replace('/user/orders');
                            }
                        }}
                    >
                        Quay lại
                    </button>
                </div>
            ) : (
                <div className={`space-y-5 ${animationsEnabled ? 'animate-in fade-in duration-500' : ''}`}>
                    {/* Header */}
                    <div className="flex items-center gap-3">
                        <button
                            onClick={() => {
                                if (window.history.length > 2) {
                                    router.back();
                                } else {
                                    router.replace('/user/orders');
                                }
                            }}
                            className="flex h-9 w-9 items-center justify-center rounded-lg border border-border bg-card transition-colors hover:border-primary hover:text-primary"
                        >
                            <ArrowLeft className="h-4 w-4" />
                        </button>
                        <h1 className="text-lg font-bold text-foreground">
                            Track &quot;<span className="text-primary">{order.maDonHang}</span>&quot;
                        </h1>
                    </div>

                    {/* Shipper Info */}
                    <div className="flex items-center gap-3 rounded-xl border border-border bg-card p-4 shadow-sm">
                        <div className="flex h-11 w-11 items-center justify-center rounded-full bg-primary/10">
                            <Truck className="h-5 w-5 text-primary" />
                        </div>
                        <div className="flex flex-1 flex-col">
                            <span className="text-sm font-semibold text-foreground">Robot Delivery</span>
                            <span className="text-xs text-muted-foreground">⭐ 4.8</span>
                        </div>
                        <div className="flex gap-2">
                            <button className="flex h-8 w-8 items-center justify-center rounded-full border border-border text-primary transition-colors hover:bg-primary/5">
                                <MessageCircle className="h-4 w-4" />
                            </button>
                            <button className="flex h-8 w-8 items-center justify-center rounded-full border border-border text-primary transition-colors hover:bg-primary/5">
                                <Phone className="h-4 w-4" />
                            </button>
                        </div>
                    </div>

                    {/* Info Cards */}
                    <div
                        className={`grid gap-2.5 ${
                            orderNeedsCompartment(order.trangThai) ? "grid-cols-3" : "grid-cols-2"
                        }`}
                    >
                        {[
                            { icon: Box, value: order.soLuong?.toString() || "1", label: "Quantity" },
                            { icon: Clock, value: order.thoiGianDuKien || "N/A", label: "Est. Time" },
                            ...(orderNeedsCompartment(order.trangThai)
                                ? [
                                      {
                                          icon: Box,
                                          value:
                                              order.compartmentId != null
                                                  ? String(order.compartmentId)
                                                  : "—",
                                          label: "Compartment ID",
                                          warn: orderCompartmentMissing(order),
                                      },
                                  ]
                                : []),
                        ].map((item) => (
                            <div
                                key={item.label}
                                className={`flex flex-col items-center gap-1.5 rounded-xl border border-border bg-card p-3 shadow-sm ${"warn" in item && item.warn ? "border-amber-500/60 bg-amber-500/5" : ""}`}
                            >
                                <item.icon className="h-4 w-4 text-primary" />
                                <span
                                    className={`text-center text-xs font-semibold break-all ${
                                        "warn" in item && item.warn ? "text-amber-700 dark:text-amber-400" : "text-foreground"
                                    }`}
                                >
                                    {item.value}
                                </span>
                                <span className="text-[10px] text-muted-foreground">{item.label}</span>
                            </div>
                        ))}
                    </div>
                    {orderNeedsCompartment(order.trangThai) && orderCompartmentMissing(order) && (
                        <p className="text-xs text-amber-600 dark:text-amber-500">
                            Trạng thái {order.trangThai === "placed" ? "đã gửi vào tủ" : "đang giao"} cần có mã ngăn tủ. Nếu
                            đơn đã nạp xong mà chưa thấy số ô, kiểm tra callback{" "}
                            <span className="font-mono">/api/dcs/deposit-closed</span> (phải gửi{" "}
                            <span className="font-mono">compartment_id</span>).
                        </p>
                    )}

                    {/* Timeline */}
                    <div className="space-y-0 pl-1">
                        {trackingSteps.map((step, idx) => {
                            const status = getStepStatus(order.trangThai, step.key);
                            const StepIcon = step.icon;
                            return (
                                <div
                                    key={step.key}
                                    className={`flex gap-4 ${animationsEnabled ? 'animate-in fade-in slide-in-from-left-2' : ''}`}
                                    style={animationsEnabled ? { animationDelay: `${idx * 100}ms`, animationFillMode: "both" } : undefined}
                                >
                                    {/* Dot + Line */}
                                    <div className="flex flex-col items-center">
                                        <div className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full transition-all ${status === "completed" ? "bg-primary text-primary-foreground shadow-sm" :
                                            status === "current" ? "border-2 border-primary bg-background text-primary ring-4 ring-primary/10" :
                                                "bg-muted text-muted-foreground"
                                            }`}>
                                            <StepIcon className="h-3.5 w-3.5" />
                                        </div>
                                        {idx < trackingSteps.length - 1 && (
                                            <div className={`my-1 h-6 w-0.5 rounded-full ${status === "completed" ? "bg-primary" :
                                                status === "current" ? "bg-linear-to-b from-primary to-border" :
                                                    "bg-border"
                                                }`} />
                                        )}
                                    </div>

                                    {/* Content */}
                                    <div className="pb-4 pt-1">
                                        <span className={`text-sm font-medium ${status === "waiting" ? "text-muted-foreground" : "text-foreground"}`}>
                                            {step.label}
                                        </span>
                                        <span className={`block text-xs ${status === "completed" ? "text-primary" :
                                            status === "current" ? "font-medium text-primary" :
                                                "text-muted-foreground"
                                            }`}>
                                            {status === "completed" && "✓ Hoàn thành"}
                                            {status === "current" && "● Hiện tại"}
                                            {status === "waiting" && "Đang chờ"}
                                        </span>
                                    </div>
                                </div>
                            );
                        })}
                    </div>

                    {/* Customer Info */}
                    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
                        <h3 className="mb-3 text-sm font-semibold text-foreground">Thông tin nhận hàng</h3>
                        {[
                            ["Khách hàng", order.tenKhachHang],
                            ["SĐT", order.sdt],
                            ["Địa chỉ", order.diaChi],
                            ["Sản phẩm", order.sanPham],
                        ].map(([label, value]) => (
                            <div key={label} className="flex justify-between border-b border-border/50 py-2 last:border-b-0">
                                <span className="text-xs text-muted-foreground">{label}</span>
                                <span className="max-w-[60%] text-right text-xs font-medium text-foreground">{value}</span>
                            </div>
                        ))}
                    </div>

                    {/* Action */}
                    {order.trangThai === "placed" && (
                        <button
                            type="button"
                            disabled={callShipLoading || orderCompartmentMissing(order)}
                            className="flex w-full items-center justify-center gap-2 rounded-xl bg-primary py-3 text-sm font-semibold text-primary-foreground shadow-sm transition-all hover:bg-primary/90 active:scale-[0.98] disabled:pointer-events-none disabled:opacity-50"
                            onClick={handleCallDelivery}
                        >
                            {callShipLoading ? (
                                <Loader2 className="h-4 w-4 animate-spin" />
                            ) : (
                                <Send className="h-4 w-4" />
                            )}
                            Gọi giao hàng
                        </button>
                    )}

                    {order.trangThai === "shipping" && order.arrivalTime && (
                        <button
                            type="button"
                            disabled={callShipLoading}
                            className="flex w-full items-center justify-center gap-2 rounded-xl bg-green-600 py-3 text-sm font-semibold text-white shadow-sm transition-all hover:bg-green-700 active:scale-[0.98] disabled:pointer-events-none disabled:opacity-50"
                            onClick={handleConfirmTakeOut}
                        >
                            {callShipLoading ? (
                                <Loader2 className="h-4 w-4 animate-spin" />
                            ) : (
                                <CheckCircle2 className="h-4 w-4" />
                            )}
                            Xác nhận lấy hàng ra
                        </button>
                    )}
                    
                    {/* PIN Code Display */}
                    {order.trangThai !== "delivered" && order.trangThai !== "cancelled" && (
                        <div className={`mt-auto pt-6 ${animationsEnabled ? 'animate-in fade-in slide-in-from-bottom-5 duration-300' : ''}`} style={animationsEnabled ? { animationDelay: "300ms", animationFillMode: "both" } : undefined}>
                            {order.trangThai === "pending" || order.trangThai === "placed" || order.trangThai === "shipping" ? (
                                <div className="rounded-2xl border border-border bg-card p-6 shadow-sm text-center">
                                    <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-primary/10 text-primary">
                                        <QrCode className="h-7 w-7" />
                                    </div>
                                    <h3 className="mb-2 text-lg font-bold text-foreground">Mã Lấy Hàng Của Bạn</h3>
                                    <div className="mb-4 inline-block rounded-lg bg-muted px-6 py-3">
                                        <span className="text-3xl font-mono font-bold tracking-widest text-primary">
                                            {order.pinCode || "------"}
                                        </span>
                                    </div>
                                    <p className="text-sm font-medium text-muted-foreground">Nhập mã PIN này trên màn hình robot để mở tủ nhận hàng</p>
                                </div>
                            ) : null}
                        </div>
                    )}

                    {/* Report */}
                    <button className="w-full rounded-xl border border-destructive/30 py-3 text-sm font-semibold text-destructive transition-colors hover:bg-destructive/5">
                        Report an Issue
                    </button>
                </div>
            )}
        </div>
    );
}
