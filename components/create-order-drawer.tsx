"use client";

import { useState } from "react";
import { Plus, Minus, Loader2, Package, User, Phone, Mail, MapPin, FileText, Box, X, AlertCircle } from "lucide-react";
import {
    Drawer,
    DrawerClose,
    DrawerContent,
    DrawerDescription,
    DrawerFooter,
    DrawerHeader,
    DrawerTitle,
    DrawerTrigger,
} from "@/components/ui/drawer";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { createOrder, type CreateOrderPayload, DEFAULT_DCS_MAP_NAME } from "@/lib/api";
import { useMapLocations } from "@/hooks/use-map-locations";
import { LocationPicker } from "@/components/location-picker";

export default function CreateOrderDrawer({ 
    siteId,
    onCreated 
}: { 
    siteId?: string | null;
    onCreated?: () => void;
}) {
    const [open, setOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    // Use the selected siteId from Postman, or fallback to default
    const effectiveSiteId = siteId || DEFAULT_DCS_MAP_NAME;
    const { locations, loading: locationsLoading } = useMapLocations({ 
        enabled: open, 
        mapName: effectiveSiteId 
    });
    
    const [form, setForm] = useState<CreateOrderPayload>({
        fullName: "",
        phone: "",
        email: "",
        address: "",
        quantity: 1,
        note: "",
    });

    const handleChange = (field: keyof CreateOrderPayload, value: string) => {
        setForm((prev) => ({
            ...prev,
            [field]: field === "quantity" ? Math.max(1, parseInt(value) || 1) : value,
        }));
    };

    const handleQuantityStep = (delta: number) => {
        setForm((prev) => ({
            ...prev,
            quantity: Math.max(1, (prev.quantity || 1) + delta),
        }));
    };

    const handleSubmit = async () => {
        if (!form.fullName || !form.phone || !form.address) return;
        setLoading(true);
        try {
            const loc = locations.find((l) => l.address === form.address.trim());
            const result = await createOrder({
                ...form,
                map_name: effectiveSiteId,
                ...(loc ? { destinationName: loc.name } : {}),
            });
            if (result) {
                toast.success(`Đã tạo đơn ${result.orderCode || result.id || ""}. Mã nhận hàng (PIN): ${result.pinCode || "---"}`, {
                    className: "!text-green-500 !border-green-600",
                });
                setOpen(false);
                setForm({ fullName: "", phone: "", email: "", address: "", quantity: 1, note: "" });
                onCreated?.();
            } else {
                toast.error("Không thể tạo đơn hàng. Vui lòng thử lại sau.", {
                    className: "!text-yellow-500 !border-yellow-600",
                });
            }
        } catch {
            toast.error("Đã xảy ra lỗi khi tạo đơn hàng", {
                className: "!text-orange-500 !border-orange-600",
            });
        } finally {
            setLoading(false);
        }
    };

    const isValid = Boolean(form.fullName.trim() && form.phone.trim() && form.address.trim());
    const missingFields: string[] = [];
    if (!form.fullName.trim()) missingFields.push("Họ tên");
    if (!form.phone.trim()) missingFields.push("SĐT");
    if (!form.address.trim()) missingFields.push("Địa chỉ");

    return (
        <Drawer open={open} onOpenChange={setOpen} dismissible={false}>
            <DrawerTrigger asChild>
                <button
                    className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-lg shadow-primary/25 transition-all hover:scale-105 hover:shadow-xl hover:shadow-primary/30 active:scale-95"
                    aria-label="Tạo đơn hàng mới"
                >
                    <Plus className="h-5 w-5" />
                </button>
            </DrawerTrigger>
            <DrawerContent className="max-h-[96dvh] h-[96dvh] sm:h-auto sm:max-h-[90dvh] flex flex-col p-0">
                <div className="mx-auto flex h-full w-full max-w-lg flex-col min-h-0 overflow-hidden bg-background">
                    {/* Accessible Header */}
                    <DrawerHeader className="shrink-0 flex flex-row items-center justify-between border-b border-border/40 px-4 py-3 bg-muted/20 text-left">
                        <div className="flex items-center gap-2.5">
                            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary/10 text-primary">
                                <Package className="h-5 w-5" />
                            </div>
                            <div className="flex flex-col">
                                <DrawerTitle className="text-base font-semibold leading-tight text-foreground">
                                    Tạo đơn hàng mới
                                </DrawerTitle>
                                <DrawerDescription className="text-xs text-muted-foreground">
                                    Điền thông tin bên dưới để tạo đơn
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

                    {/* Scrollable Form Body */}
                    <div className="flex-1 min-h-0 overflow-y-auto overscroll-contain px-4 py-3.5 space-y-4 custom-scrollbar">
                        {/* Section 1: Customer Info */}
                        <div className="rounded-2xl border border-border/60 bg-card/60 p-3.5 space-y-3.5 shadow-2xs">
                            <div className="flex items-center gap-1.5 text-xs font-semibold text-primary uppercase tracking-wider">
                                <User className="h-3.5 w-3.5" />
                                Người nhận hàng
                            </div>

                            <div className="space-y-1.5">
                                <Label htmlFor="fullName" className="text-xs font-medium text-muted-foreground flex items-center gap-1">
                                    Họ tên khách hàng <span className="text-destructive">*</span>
                                </Label>
                                <Input
                                    id="fullName"
                                    placeholder="Nguyễn Văn A"
                                    value={form.fullName}
                                    onChange={(e) => handleChange("fullName", e.target.value)}
                                    autoComplete="name"
                                    className="h-11 rounded-xl border-border/60 bg-muted/30 px-3 transition-all focus:bg-background focus:ring-2 focus:ring-primary/20"
                                />
                            </div>

                            <div className="space-y-1.5">
                                <Label htmlFor="phone" className="text-xs font-medium text-muted-foreground flex items-center gap-1">
                                    Số điện thoại <span className="text-destructive">*</span>
                                </Label>
                                <Input
                                    id="phone"
                                    type="tel"
                                    inputMode="tel"
                                    autoComplete="tel"
                                    placeholder="0901234567"
                                    value={form.phone}
                                    onChange={(e) => handleChange("phone", e.target.value)}
                                    className="h-11 rounded-xl border-border/60 bg-muted/30 px-3 text-base transition-all focus:bg-background focus:ring-2 focus:ring-primary/20"
                                />
                            </div>

                            <div className="space-y-1.5">
                                <Label htmlFor="email" className="text-xs font-medium text-muted-foreground flex items-center justify-between">
                                    <span className="flex items-center gap-1">
                                        <Mail className="h-3.5 w-3.5" />
                                        Email
                                    </span>
                                    <span className="text-[10px] text-muted-foreground font-normal">Không bắt buộc</span>
                                </Label>
                                <Input
                                    id="email"
                                    type="email"
                                    inputMode="email"
                                    autoComplete="email"
                                    placeholder="example@gmail.com"
                                    value={form.email ?? ""}
                                    onChange={(e) => handleChange("email", e.target.value)}
                                    className="h-11 rounded-xl border-border/60 bg-muted/30 px-3 transition-all focus:bg-background focus:ring-2 focus:ring-primary/20"
                                />
                                <p className="text-[11px] text-muted-foreground">
                                    💡 Mã PIN bí mật sẽ gửi về email này khi hàng vào tủ robot
                                </p>
                            </div>
                        </div>

                        {/* Section 2: Delivery & Goods Info */}
                        <div className="rounded-2xl border border-border/60 bg-card/60 p-3.5 space-y-3.5 shadow-2xs">
                            <div className="flex items-center gap-1.5 text-xs font-semibold text-primary uppercase tracking-wider">
                                <Box className="h-3.5 w-3.5" />
                                Hàng hóa & Địa điểm
                            </div>

                            <div className="space-y-1.5">
                                <Label className="text-xs font-medium text-muted-foreground flex items-center gap-1">
                                    <MapPin className="h-3.5 w-3.5" />
                                    Địa chỉ giao hàng <span className="text-destructive">*</span>
                                </Label>
                                <LocationPicker
                                    locations={locations}
                                    loading={locationsLoading}
                                    value={form.address}
                                    onChange={(val) => handleChange("address", val)}
                                />
                            </div>

                            {/* Quantity Stepper */}
                            <div className="space-y-1.5">
                                <Label htmlFor="quantity" className="text-xs font-medium text-muted-foreground">
                                    Số lượng kiện hàng
                                </Label>
                                <div className="flex items-center justify-between gap-3">
                                    <div className="flex items-center rounded-xl border border-border/60 bg-muted/30 p-1">
                                        <button
                                            type="button"
                                            onClick={() => handleQuantityStep(-1)}
                                            disabled={(form.quantity || 1) <= 1}
                                            className="flex h-9 w-9 items-center justify-center rounded-lg bg-background text-foreground shadow-xs transition-all hover:bg-muted active:scale-95 disabled:opacity-40"
                                            aria-label="Giảm số lượng"
                                        >
                                            <Minus className="h-4 w-4" />
                                        </button>
                                        <span className="w-12 text-center text-sm font-semibold text-foreground">
                                            {form.quantity || 1}
                                        </span>
                                        <button
                                            type="button"
                                            onClick={() => handleQuantityStep(1)}
                                            className="flex h-9 w-9 items-center justify-center rounded-lg bg-background text-foreground shadow-xs transition-all hover:bg-muted active:scale-95"
                                            aria-label="Tăng số lượng"
                                        >
                                            <Plus className="h-4 w-4" />
                                        </button>
                                    </div>
                                    <div className="flex gap-1.5">
                                        {[1, 2, 5, 10].map((q) => (
                                            <button
                                                key={q}
                                                type="button"
                                                onClick={() => handleChange("quantity", String(q))}
                                                className={`rounded-lg px-2.5 py-1.5 text-xs font-medium transition-all active:scale-95 ${
                                                    form.quantity === q
                                                        ? "bg-primary text-primary-foreground font-semibold shadow-xs"
                                                        : "border border-border/60 bg-muted/40 text-muted-foreground hover:bg-muted"
                                                }`}
                                            >
                                                {q}
                                            </button>
                                        ))}
                                    </div>
                                </div>
                            </div>

                            <div className="space-y-1.5">
                                <Label htmlFor="note" className="text-xs font-medium text-muted-foreground flex items-center gap-1">
                                    <FileText className="h-3.5 w-3.5" />
                                    Tên sản phẩm / Ghi chú
                                </Label>
                                <Input
                                    id="note"
                                    placeholder="VD: Tài liệu, hộp quà, đồ ăn..."
                                    value={form.note ?? ""}
                                    onChange={(e) => handleChange("note", e.target.value)}
                                    className="h-11 rounded-xl border-border/60 bg-muted/30 px-3 transition-all focus:bg-background focus:ring-2 focus:ring-primary/20"
                                />
                            </div>
                        </div>

                        {/* Validation helper notice */}
                        {!isValid && (
                            <div className="flex items-center gap-2 rounded-xl bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
                                <AlertCircle className="h-4 w-4 shrink-0 text-amber-500" />
                                <span>Còn thiếu: <strong className="text-foreground">{missingFields.join(", ")}</strong></span>
                            </div>
                        )}
                    </div>

                    {/* Fixed Bottom Footer */}
                    <div className="shrink-0 border-t border-border/40 bg-background/95 backdrop-blur-md p-4 pb-6 sm:pb-4 shadow-lg">
                        <div className="flex w-full gap-2.5">
                            <DrawerClose asChild>
                                <Button variant="outline" className="h-12 flex-1 rounded-xl text-sm font-medium">
                                    Hủy bỏ
                                </Button>
                            </DrawerClose>
                            <Button
                                onClick={handleSubmit}
                                disabled={!isValid || loading}
                                className="h-12 flex-[2] rounded-xl text-sm font-semibold shadow-md shadow-primary/25 transition-all hover:shadow-lg hover:shadow-primary/35 disabled:opacity-50"
                            >
                                {loading ? (
                                    <>
                                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                        Đang tạo...
                                    </>
                                ) : (
                                    <>
                                        <Plus className="mr-2 h-4 w-4" />
                                        Tạo đơn hàng
                                    </>
                                )}
                            </Button>
                        </div>
                    </div>
                </div>
            </DrawerContent>
        </Drawer>
    );
}
