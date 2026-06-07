"use client";

import { useState } from "react";
import { Plus, Loader2, Package, User, Phone, MapPin, FileText, Box } from "lucide-react";
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
        address: "",
        quantity: 1,
        note: "",
    });

    const handleChange = (field: keyof CreateOrderPayload, value: string) => {
        setForm((prev) => ({
            ...prev,
            [field]: field === "quantity" ? parseInt(value) || 0 : value,
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
                setForm({ fullName: "", phone: "", address: "", quantity: 1, note: "" });
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

    const isValid = form.fullName && form.phone && form.address;

    return (
        <Drawer open={open} onOpenChange={setOpen}>
            <DrawerTrigger asChild>
                <button
                    className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-lg shadow-primary/25 transition-all hover:scale-105 hover:shadow-xl hover:shadow-primary/30 active:scale-95"
                    aria-label="Tạo đơn hàng mới"
                >
                    <Plus className="h-5 w-5" />
                </button>
            </DrawerTrigger>
            <DrawerContent>
                <div className="mx-auto w-full max-w-md">
                    <DrawerHeader>
                        <DrawerTitle className="flex items-center gap-2 text-lg">
                            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10">
                                <Package className="h-4 w-4 text-primary" />
                            </div>
                            Tạo đơn hàng mới
                        </DrawerTitle>
                        <DrawerDescription>
                            Điền thông tin bên dưới để tạo đơn hàng
                        </DrawerDescription>
                    </DrawerHeader>

                    <div className="space-y-4 px-4 pb-2">
                        <div className="space-y-1.5">
                            <Label htmlFor="fullName" className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                                <User className="h-3.5 w-3.5" />
                                Họ tên khách hàng
                                <span className="text-destructive">*</span>
                            </Label>
                            <Input
                                id="fullName"
                                placeholder="Nguyễn Văn A"
                                value={form.fullName}
                                onChange={(e) => handleChange("fullName", e.target.value)}
                                className="h-11 rounded-xl border-border/60 bg-muted/30 transition-all focus:bg-background focus:ring-2 focus:ring-primary/20"
                            />
                        </div>

                        <div className="space-y-1.5">
                            <Label htmlFor="phone" className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                                <Phone className="h-3.5 w-3.5" />
                                Số điện thoại
                                <span className="text-destructive">*</span>
                            </Label>
                            <Input
                                id="phone"
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
                                value={form.address}
                                onChange={(val) => handleChange("address", val)}
                            />
                        </div>

                        <div className="space-y-1.5">
                            <Label htmlFor="quantity" className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                                <Box className="h-3.5 w-3.5" />
                                Số lượng
                            </Label>
                            <Input
                                id="quantity"
                                type="number"
                                placeholder="1"
                                value={form.quantity ?? ""}
                                onChange={(e) => handleChange("quantity", e.target.value)}
                                className="h-11 rounded-xl border-border/60 bg-muted/30 transition-all focus:bg-background focus:ring-2 focus:ring-primary/20"
                            />
                        </div>

                        <div className="space-y-1.5">
                            <Label htmlFor="note" className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                                <FileText className="h-3.5 w-3.5" />
                                Sản phẩm / Ghi chú
                            </Label>
                            <Input
                                id="note"
                                placeholder="iPhone 15 Pro Max"
                                value={form.note ?? ""}
                                onChange={(e) => handleChange("note", e.target.value)}
                                className="h-11 rounded-xl border-border/60 bg-muted/30 transition-all focus:bg-background focus:ring-2 focus:ring-primary/20"
                            />
                        </div>
                    </div>

                    <DrawerFooter>
                        <Button
                            onClick={handleSubmit}
                            disabled={!isValid || loading}
                            className="h-12 w-full rounded-xl text-base font-semibold shadow-md shadow-primary/20 transition-all hover:shadow-lg hover:shadow-primary/30"
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
                        <DrawerClose asChild>
                            <Button variant="outline" className="h-11 w-full rounded-xl">
                                Hủy bỏ
                            </Button>
                        </DrawerClose>
                    </DrawerFooter>
                </div>
            </DrawerContent>
        </Drawer>
    );
}
