import { Order, OrderStatus } from "./orders";

export type BackendOrder = {
    id: string;
    createdTime: string;
    completedTime?: string;
    arrivalTime?: string;
    fullName: string;
    phone: string;
    address: string;
    quantity?: number;
    note: string;
    status: string;
    orderCode: string;
    pinCode?: string;
    // Campus / place label for robot destination.name when saved from location picker
    destinationName?: string | null;
    // Mongo `maps` key / DCS map id for this order
    map_name?: string | null;
    compartmentId?: number | null;
    // Some JSON stacks may serialize as snake_case
    compartment_id?: number | null;
};

function backendCompartmentId(b: BackendOrder): number | null {
    const v = b.compartmentId ?? b.compartment_id;
    if (v === undefined || v === null) return null;
    return v;
}

// Mappers from backend OrderModel to frontend Order
export function mapBackendOrderToFrontend(b: BackendOrder): Order {
    return {
        maDonHang: b.orderCode || b.id,
        pinCode: b.pinCode,
        sanPham: b.note || "N/A", // Use note for 'sanPham'
        tenKhachHang: b.fullName || "Guest",
        sdt: b.phone || "",
        diaChi: b.address || "",
        destinationName: b.destinationName?.trim() || undefined,
        // Make sure status matches our literal types, fallback to pending if unrecognized
        trangThai: ["pending", "placed", "shipping", "delivered", "cancelled"].includes(b.status)
            ? (b.status as OrderStatus)
            : "pending",
        ngayGui: b.createdTime,
        thoiGianDuKien: "Unknown", // Backend doesn't have estimate time, just use Unknown
        soLuong: b.quantity, // <--- Added mapping for new quantity field
        compartmentId: backendCompartmentId(b),
        arrivalTime: b.arrivalTime,
    };
}

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";

// Must match backend `vtmen.dcs.map-name` for the default campus map document in Mongo `maps`.
export const DEFAULT_DCS_MAP_NAME =
    process.env.NEXT_PUBLIC_DCS_MAP_NAME || "Trường đại học";

export type MapLocationPoint = { id: number; name: string; address: string; dockName?: string; dockShowName?: string };

// Same shape as DCS GET `/task/interface/getParkPointPage`.
export type DcsMapPoint = {
    parkPointId: string;
    dockName: string;
    dockShowName?: string;
    longitude?: number;
    latitude?: number;
};
export type DcsLocationsEnvelope = {
    code?: number | null;
    msg?: string | null;
    data?: {
        total?: number;
        records?: DcsMapPoint[];
    } | null;
};

export type DcsSite = {
    siteId: string;
    siteName: string;
    longitude: number;
    latitude: number;
    siteStatus: number;
};

export type DcsSiteEnvelope = {
    code?: number | null;
    msg?: string | null;
    data?: {
        total?: number;
        records?: DcsSite[];
    } | null;
};

export async function fetchSites(): Promise<DcsSite[]> {
    try {
        const res = await fetch(`${API_BASE}/maps/sites`, { cache: "no-store" });
        if (!res.ok) throw new Error("Failed to fetch sites");
        const env: DcsSiteEnvelope = await res.json();
        return env.data?.records || [];
    } catch (e) {
        console.warn("fetchSites API Error:", e instanceof Error ? e.message : String(e));
        return [];
    }
}

function mapQuery(mapName?: string): string {
    return mapName != null && mapName !== ""
        ? `?map_name=${encodeURIComponent(mapName)}`
        : `?map_name=${encodeURIComponent(DEFAULT_DCS_MAP_NAME)}`;
}

// Live DCS POIs: proxy to DCS GET /task/interface/getParkPointPage
export async function fetchDcsMapLocations(mapName?: string): Promise<DcsLocationsEnvelope> {
    const url = `${API_BASE}/maps/dcs${mapQuery(mapName)}`;
    const res = await fetch(url, { cache: "no-store" });
    if (!res.ok) {
        throw new Error(`DCS map proxy failed: ${res.status} ${res.statusText} (${url})`);
    }
    return res.json();
}

// Maps DCS-style points to picker rows (coordinates kept on raw envelope if you need them later).
export function dcsEnvelopeToMapPoints(env: DcsLocationsEnvelope): MapLocationPoint[] {
    const raw = env?.data?.records;
    if (!raw?.length) return [];
    const out: MapLocationPoint[] = [];
    let i = 0;
    for (const p of raw) {
        const id = p.parkPointId;
        const name = (p.dockShowName || p.dockName)?.trim() ?? "";
        // For backwards compatibility or mapping we use parkPointId as the id and name
        const address = name; // We just use the name as address for UI picker
        if (!name) continue;
        out.push({ id: i++, name: id, address, dockName: p.dockName, dockShowName: p.dockShowName });
    }
    return out;
}

// POIs from Mongo `maps` collection (seed or DCS sync).
export async function fetchMapPoints(mapName?: string): Promise<MapLocationPoint[]> {
    const url = `${API_BASE}/maps/points${mapQuery(mapName)}`;
    const res = await fetch(url, { cache: "no-store" });
    if (!res.ok) {
        throw new Error(`Failed to fetch map points: ${res.status} ${res.statusText} (${url})`);
    }
    return res.json();
}

export async function fetchActiveOrders(): Promise<Order[]> {
    try {
        const res = await fetch(`${API_BASE}/orders/active`, { cache: "no-store" });
        if (!res.ok) throw new Error("Failed to fetch active orders");
        const data: BackendOrder[] = await res.json();
        return data.map(mapBackendOrderToFrontend);
    } catch (error) {
        console.warn("fetchActiveOrders API Error:", error instanceof Error ? error.message : String(error));
        return [];
    }
}

export async function fetchOrderHistory(): Promise<Order[]> {
    try {
        const res = await fetch(`${API_BASE}/orders/history`, { cache: "no-store" });
        if (!res.ok) throw new Error("Failed to fetch order history");
        const data: BackendOrder[] = await res.json();
        return data.map(mapBackendOrderToFrontend);
    } catch (error) {
        console.warn("fetchOrderHistory API Error:", error instanceof Error ? error.message : String(error));
        return [];
    }
}

export async function completeOrderApi(id: string): Promise<boolean> {
    try {
        const res = await fetch(`${API_BASE}/orders/${id}/complete`, {
            method: "POST",
        });
        return res.ok;
    } catch (error) {
        console.warn("completeOrderApi API Error:", error instanceof Error ? error.message : String(error));
        return false;
    }
}

export type CreateOrderPayload = {
    fullName: string;
    phone: string;
    address: string;
    destinationName?: string;
    map_name?: string;
    quantity?: number;
    note: string;
    status?: string;
};

export async function createOrder(payload: CreateOrderPayload): Promise<BackendOrder | null> {
    try {
        const res = await fetch(`${API_BASE}/orders/create`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                ...payload,
                status: payload.status || "pending",
            }),
        });
        if (!res.ok) throw new Error("Failed to create order");
        const order: BackendOrder = await res.json();
        return order;
    } catch (error) {
        console.warn("createOrder API Error:", error instanceof Error ? error.message : String(error));
        return null;
    }
}

export type UpdateOrderPayload = {
    fullName?: string;
    phone?: string;
    address?: string;
    destinationName?: string;
    map_name?: string;
    quantity?: number;
    note?: string;
};

export async function updateOrder(orderId: string, payload: UpdateOrderPayload): Promise<BackendOrder | null> {
    try {
        const res = await fetch(`${API_BASE}/orders/${orderId}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        });
        if (!res.ok) throw new Error("Failed to update order");
        return await res.json();
    } catch (error) {
        console.warn("updateOrder API Error:", error instanceof Error ? error.message : String(error));
        return null;
    }
}

export async function cancelOrder(orderId: string): Promise<boolean> {
    try {
        const res = await fetch(`${API_BASE}/orders/${orderId}/cancel`, {
            method: "POST",
        });
        return res.ok;
    } catch (error) {
        console.warn("cancelOrder API Error:", error instanceof Error ? error.message : String(error));
        return false;
    }
}

export async function confirmOrderPlaced(orderId: string, compartmentId: number): Promise<boolean> {
    try {
        const res = await fetch(`${API_BASE}/orders/${encodeURIComponent(orderId)}/status`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ status: "placed", compartmentId }),
        });
        return res.ok;
    } catch (error) {
        console.warn("confirmOrderPlaced API Error:", error instanceof Error ? error.message : String(error));
        return false;
    }
}

export async function cancelDelivery(orderId: string): Promise<boolean> {
    try {
        const res = await fetch(`${API_BASE}/orders/${orderId}/cancel-delivery`, {
            method: "POST",
        });
        return res.ok;
    } catch (error) {
        console.warn("cancelDelivery API Error:", error instanceof Error ? error.message : String(error));
        return false;
    }
}

export type DispatchRobotPayload = {
    robot_id?: string;
    destination?: { name?: string; address_text?: string };
};

export type DispatchRobotResponse = {
    order: BackendOrder;
    robot_status: string;
    message: string;
    estimated_time_of_arrival?: number;
};

// Calls backend → DCS sendtaskVtMen; backend sets order to shipping on SUCCESS.
export async function dispatchRobot(
    orderCode: string,
    payload?: DispatchRobotPayload
): Promise<DispatchRobotResponse> {
    const res = await fetch(
        `${API_BASE}/orders/${encodeURIComponent(orderCode)}/dispatch-robot`,
        {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload ?? {}),
        }
    );
    if (!res.ok) {
        let detail = res.statusText;
        try {
            const text = await res.text();
            if (text) {
                try {
                    const j = JSON.parse(text) as { message?: string };
                    if (j.message) detail = j.message;
                    else detail = text;
                } catch {
                    detail = text;
                }
            }
        } catch {
            // keep statusText
        }
        throw new Error(detail);
    }
    return res.json() as Promise<DispatchRobotResponse>;
}
