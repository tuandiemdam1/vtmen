"use client";

import { useState, useEffect } from "react";
import { fetchSites, type DcsSite } from "@/lib/api";

export function useSites() {
    const [sites, setSites] = useState<DcsSite[]>([]);
    const [loading, setLoading] = useState(true);
    const [lastUsedSiteId, setLastUsedSiteId] = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;

        async function loadSites() {
            setLoading(true);
            const data = await fetchSites();
            if (cancelled) return;
            setSites(data);
            
            if (data.length > 0) {
                const storedSiteId = localStorage.getItem("last_used_site_id");
                if (storedSiteId && data.some(s => s.siteId === storedSiteId)) {
                    setLastUsedSiteId(storedSiteId);
                } else {
                    setLastUsedSiteId(data[0].siteId);
                }
                setLoading(false);
            } else {
                setLoading(false);
            }
        }

        loadSites();

        return () => {
            cancelled = true;
        };
    }, []);

    return { sites, loading, lastUsedSiteId } as const;
}
