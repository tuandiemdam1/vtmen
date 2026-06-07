"use client";

import { useState, useEffect } from "react";
import { toast } from "sonner";
import {
    fetchDcsMapLocations,
    dcsEnvelopeToMapPoints,
    DEFAULT_DCS_MAP_NAME,
    type MapLocationPoint,
    fetchMapPoints,
} from "@/lib/api";

export type UseMapLocationsOptions = {
    // Mongo / DCS map id; defaults to {@link DEFAULT_DCS_MAP_NAME}.
    mapName?: string;
    // When false, no request runs (e.g. drawer closed).
    enabled?: boolean;
};

// Tries live DCS data ({@code GET /api/maps/dcs}), then Mongo ({@code /maps/points}), then {@link getFallbackMapPoints}.
export function useMapLocations(options: UseMapLocationsOptions = {}) {
    const mapName = options.mapName ?? DEFAULT_DCS_MAP_NAME;
    const enabled = options.enabled ?? true;

    const [locations, setLocations] = useState<MapLocationPoint[]>([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (!enabled) {
            const timeoutId = setTimeout(() => setLoading(false), 0);
            return () => clearTimeout(timeoutId);
        }
        let cancelled = false;
        setTimeout(() => {
            if (!cancelled) setLoading(true);
        }, 0);

        (async () => {
            let pts: MapLocationPoint[] = [];
            try {
                const env = await fetchDcsMapLocations(mapName);
                pts = dcsEnvelopeToMapPoints(env);
            } catch (error) {
                console.warn("DCS fetch error, falling back to Mongo:", error);
            }

            if (!cancelled && pts.length === 0) {
                try {
                    pts = await fetchMapPoints(mapName);
                } catch (mongoError) {
                    console.error("Mongo fetch error:", mongoError);
                }
            }
            
            if (!cancelled) {
                if (pts.length > 0) {
                    setLocations(pts);
                } else {
                    setLocations([]);
                    toast.error("Không thể lấy danh sách địa điểm.");
                }
            }
            
            if (!cancelled) {
                setLoading(false);
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [enabled, mapName]);

    return { locations, loading } as const;
}
