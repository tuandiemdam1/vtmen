"use client";

import { useState, useEffect } from "react";
import { useMapLocations } from "@/hooks/use-map-locations";
import { Loader2, Pencil, User, Phone, MapPin, FileText, Box, X } from "lucide-react";
import {
    Drawer,
    DrawerClose,
    DrawerContent,
    DrawerDescription,
    DrawerFooter,
    DrawerHeader,
    DrawerTitle,
} from "@/components/ui/drawer";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { updateOrder, type UpdateOrderPayload, DEFAULT_DCS_MAP_NAME } from "@/lib/api";
import { type Order } from "@/lib/orders";
import { LocationPicker } from "@/components/location-picker";

interface UpdateOrderDrawerProps {
    order: Order;
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onUpdated?: () => void;
}

export default function UpdateOrderDrawer({ order, open, onOpenChange, onUpdated }: UpdateOrderDrawerProps) {
    const [loading, setLoading] = useState(false);
    const { locations, loading: locationsLoading } = useMapLocations({ enabled: open });
    const [form, setForm] = useState<UpdateOrderPayload>({
        fullName: "",
        phone: "",
        address: "",
        quantity: 1,
        note: "",
    });

    useEffect(() => {
        if (open && order) {
            setForm({
                fullName: order.tenKhachHang || "",
                phone: order.sdt || "",
                address: order.diaChi || "",
                quantity: order.soLuong || 1,
                note: order.sanPham || "",
            });
        }
    }, [open, order]);

    const handleChange = (field: keyof UpdateOrderPayload, value: string) => {
        setForm((prev) => ({
            ...prev,
            [field]: field === "quantity" ? parseInt(value) || 0 : value,
        }));
    };

    const handleSubmit = async () => {
        if (!form.fullName || !form.phone || !form.address) return;
        setLoading(true);
        try {
            const loc = locations.find((l) => l.address === (form.address ?? "").trim());
            const result = await updateOrder(order.maDonHang, {
                ...form,
                map_name: DEFAULT_DCS_MAP_NAME,
                destinationName: loc?.name ?? "",
            });
            if (result) {
                toast.success(`Đã cập nhật đơn hàng ${order.maDonHang}`, {
                    className: "!text-blue-500 !border-blue-600",
                });
                onOpenChange(false);
                onUpdated?.();
            } else {
                toast.error("Không thể cập nhật đơn hàng. Vui lòng thử lại sau.", {
                    className: "!text-yellow-500 !border-yellow-600",
                });
            }
        } catch {
            toast.error("Đã xảy ra lỗi khi cập nhật đơn hàng", {
                className: "!text-orange-500 !border-orange-600",
            });
        } finally {
            setLoading(false);
        }
    };

    const isValid = form.fullName && form.phone && form.address;

    return (
        <Drawer open={open} onOpenChange={onOpenChange} dismissible={false}>
            <DrawerContent className="max-h-[96dvh] h-[96dvh] sm:h-auto sm:max-h-[92dvh] flex flex-col p-0">
                <div className="mx-auto flex h-full w-full max-w-md flex-col min-h-0 overflow-hidden">
                    <DrawerHeader className="shrink-0 flex flex-row items-center justify-between border-b border-border/40 px-4 py-3 bg-muted/20 text-left">
                        <div className="flex items-center gap-2.5">
                            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-500/10">
                                <Pencil className="h-4 w-4 text-blue-500" />
                            </div>
                            <div className="flex flex-col">
                                <DrawerTitle className="text-base font-semibold leading-tight">
                                    Cập nhật đơn hàng
                                </DrawerTitle>
                                <DrawerDescription className="text-xs text-muted-foreground">
                                    Chỉnh sửa đơn <span className="font-semibold text-foreground">{order.maDonHang}</span>
                                </DrawerDescription>
                            </div>
                        </div>
                        <DrawerClose asChild>
                            <button
                                type="button"
                                className="flex h-8 w-8 items-center justify-center rounded-lg text-muted-foreground hover:bg-muted hover:text-foreground active:scale-95"
                                aria-label="Đóng"
                            >
                                <X className="h-5 w-5" />
                            </button>
                        </DrawerClose>
                    </DrawerHeader>

                    <div className="flex-1 min-h-0 overflow-y-auto overscroll-contain px-4 py-4 space-y-4 custom-scrollbar">
                        <div className="space-y-1.5">
                            <Label htmlFor="update-fullName" className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                                <User className="h-3.5 w-3.5" />
                                Họ tên khách hàng
                                <span className="text-destructive">*</span>
                            </Label>
                            <Input
                                id="update-fullName"
                                placeholder="Nguyễn Văn A"
                                value={form.fullName}
                                onChange={(e) => handleChange("fullName", e.target.value)}
                                autoComplete="name"
                                className="h-11 rounded-xl border-border/60 bg-muted/30 transition-all focus:bg-background focus:ring-2 focus:ring-primary/20"
                            />
                        </div>

                        <div className="space-y-1.5">
                            <Label htmlFor="update-phone" className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                                <Phone className="h-3.5 w-3.5" />
                                Số điện thoại
                                <span className="text-destructive">*</span>
                            </Label>
                            <Input
                                id="update-phone"
                                type="tel"
                                inputMode="tel"
                                autoComplete="tel"
                                placeholder="0901234567"
                                value={form.phone}
                                onChange={(e) => handleChange("phone", e.target.value)}
                                className="h-11 rounded-xl border-border/60 bg-muted/30 transition-all focus:bg-background focus:ring-2 focus:ring-primary/20"
                            />
                        </div>

                        <div className="space-y-1.5">
                            <Label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                                <MapPin className="h-3.5 w-3.5" />
                                Địa chỉ giao hàng
                                <span className="text-destructive">*</span>
                            </Label>
                            <LocationPicker
                                locations={locations}
                                loading={locationsLoading}
                                value={form.address ?? ""}
                                onChange={(val) => handleChange("address", val)}
                            />
                        </div>

                        <div className="space-y-1.5">
                            <Label htmlFor="update-quantity" className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                                <Box className="h-3.5 w-3.5" />
                                Số lượng
                            </Label>
                            <Input
                                id="update-quantity"
                                type="number"
                                placeholder="1"
                                value={form.quantity ?? ""}
                                onChange={(e) => handleChange("quantity", e.target.value)}
                                className="h-11 rounded-xl border-border/60 bg-muted/30 transition-all focus:bg-background focus:ring-2 focus:ring-primary/20"
                            />
                        </div>

                        <div className="space-y-1.5">
                            <Label htmlFor="update-note" className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                                <FileText className="h-3.5 w-3.5" />
                                Sản phẩm / Ghi chú
                            </Label>
                            <Input
                                id="update-note"
                                placeholder="iPhone 15 Pro Max"
                                value={form.note ?? ""}
                                onChange={(e) => handleChange("note", e.target.value)}
                                className="h-11 rounded-xl border-border/60 bg-muted/30 transition-all focus:bg-background focus:ring-2 focus:ring-primary/20"
                            />
                        </div>
                    </div>

                    <DrawerFooter className="shrink-0 border-t border-border/40 bg-background/95 backdrop-blur-sm p-4 pb-6 sm:pb-4">
                        <div className="flex w-full gap-2.5">
                            <DrawerClose asChild>
                                <Button variant="outline" className="h-11 flex-1 rounded-xl">
                                    Hủy bỏ
                                </Button>
                            </DrawerClose>
                            <Button
                                onClick={handleSubmit}
                                disabled={!isValid || loading}
                                className="h-11 flex-[2] rounded-xl bg-blue-600 text-sm font-semibold text-white shadow-md transition-all hover:bg-blue-700 hover:shadow-lg disabled:opacity-50"
                            >
                                {loading ? (
                                    <>
                                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                        Đang cập nhật...
                                    </>
                                ) : (
                                    <>
                                        <Pencil className="mr-2 h-4 w-4" />
                                        Cập nhật đơn hàng
                                    </>
                                )}
                            </Button>
                        </div>
                    </DrawerFooter>
                </div>
            </DrawerContent>
        </Drawer>
    );
}
