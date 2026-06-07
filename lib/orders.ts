export type OrderStatus =
    | "pending"
    | "placed"      // đã gửi vào tủ, chưa giao
    | "shipping"    // robot đang giao
    | "delivered"
    | "cancelled";

export type Order = {
    maDonHang: string;
    pinCode?: string;
    sanPham: string;
    tenKhachHang: string;
    sdt: string;
    diaChi: string;
    // POI / campus name for robot (from backend `destinationName`)
    destinationName?: string;
    trangThai: OrderStatus;
    ngayGui: string;
    thoiGianDuKien: string;
    soLuong?: number; // Added new field for quantity
    // Set when DCS reports deposit closed; required for placed/shipping UX
    compartmentId?: number | null;
    arrivalTime?: string; // Khi robot đến nơi
};

export function orderNeedsCompartment(status: OrderStatus): boolean {
    return status === "placed" || status === "shipping";
}

export function orderCompartmentMissing(order: Order): boolean {
    return (
        orderNeedsCompartment(order.trangThai) &&
        (order.compartmentId === null || order.compartmentId === undefined)
    );
}

export const statusLabels: Record<OrderStatus, string> = {
    pending: "Pending",
    placed: "Placed",
    shipping: "Shipping",
    delivered: "Completed",
    cancelled: "Cancelled",
};

// Note: The static 'orders' array was removed. Use fetchActiveOrders() and fetchOrderHistory() from lib/api.ts instead.
