"use client";

import Link from "next/link";
import OrderCard from "@/components/order-card";
import CreateOrderDrawer from "@/components/create-order-drawer";
import { TruckElectric, RefreshCw, History, MapPin } from "lucide-react";
import { useState, useEffect } from "react";
import { useSwipeBack } from "@/hooks/use-swipe-back";
import { useScrollRestoration } from "@/hooks/use-scroll-restoration";
import { useSites } from "@/hooks/use-sites";

export default function Page() {
    const [refreshKey, setRefreshKey] = useState(0);
    const [isRefreshing, setIsRefreshing] = useState(false);
    useSwipeBack('/');
    useScrollRestoration();

    const { sites, loading: sitesLoading, lastUsedSiteId } = useSites();
    const [selectedSiteId, setSelectedSiteId] = useState<string | null>(null);

    // Default to last used site once determined
    useEffect(() => {
        if (lastUsedSiteId && !selectedSiteId) {
            const timeoutId = setTimeout(() => setSelectedSiteId(lastUsedSiteId), 0);
            return () => clearTimeout(timeoutId);
        }
    }, [lastUsedSiteId, selectedSiteId]);

    const handleSiteChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
        const val = e.target.value;
        setSelectedSiteId(val);
        localStorage.setItem("last_used_site_id", val);
    };

    const triggerRefreshAnimation = () => {
        setIsRefreshing(true);
        setTimeout(() => setIsRefreshing(false), 500);
    };

    const handleRefresh = () => {
        setRefreshKey((k) => k + 1);
        triggerRefreshAnimation();
    };

    const handleDataChange = () => {
        // When data changes via WebSocket, play the same animation
        triggerRefreshAnimation();
    };

    return (
        <div className="mx-auto flex min-h-screen max-w-md flex-col bg-background">
            {/* Premium Header */}
            <div className="sticky top-0 z-20 border-b border-border/40 bg-background/80 backdrop-blur-xl">
                <div className="flex items-center justify-between px-4 py-4">
                    <div className="flex items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-linear-to-br from-primary to-primary/70 shadow-md shadow-primary/20">
                            <TruckElectric className="h-5 w-5 text-primary-foreground" />
                        </div>
                        <div>
                            <h1 className="text-lg font-bold text-foreground">Đơn hàng</h1>
                            <p className="text-xs text-muted-foreground">Quản lý giao hàng</p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        <Link
                            href="/postman/history"
                            className="flex h-10 w-10 items-center justify-center rounded-xl border border-border/60 bg-card text-muted-foreground transition-all hover:border-primary/50 hover:text-primary active:scale-95"
                            aria-label="Lịch sử"
                        >
                            <History className="h-4 w-4" />
                        </Link>
                        <button
                            onClick={handleRefresh}
                            className="flex h-10 w-10 items-center justify-center rounded-xl border border-border/60 bg-card text-muted-foreground transition-all hover:border-primary/50 hover:text-primary active:scale-95"
                            aria-label="Làm mới"
                        >
                            <RefreshCw className={`h-4 w-4 ${isRefreshing ? "animate-spin" : ""}`} />
                        </button>
                        <CreateOrderDrawer 
                            siteId={selectedSiteId} 
                            onCreated={() => setRefreshKey((k) => k + 1)} 
                        />
                    </div>
                </div>
                
                {/* Site Selection Bar */}
                <div className="px-4 pb-3">
                    <div className="relative flex items-center">
                        <div className="pointer-events-none absolute left-3 flex h-full items-center">
                            <MapPin className="h-4 w-4 text-primary" />
                        </div>
                        <select
                            value={selectedSiteId || ""}
                            onChange={handleSiteChange}
                            disabled={sitesLoading || sites.length === 0}
                            className="h-10 w-full appearance-none rounded-xl border border-border/60 bg-muted/30 pl-10 pr-4 text-sm font-medium text-foreground transition-all focus:border-primary/50 focus:outline-none focus:ring-1 focus:ring-primary/50 disabled:opacity-50"
                        >
                            {sitesLoading ? (
                                <option value="">Đang tải danh sách trạm...</option>
                            ) : sites.length === 0 ? (
                                <option value="">Không có trạm nào</option>
                            ) : (
                                sites.map(site => (
                                    <option key={site.siteId} value={site.siteId}>
                                        {site.siteName}
                                    </option>
                                ))
                            )}
                        </select>
                        <div className="pointer-events-none absolute right-3 flex h-full items-center">
                            <svg className="h-4 w-4 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
                            </svg>
                        </div>
                    </div>
                </div>
            </div>

            {/* Order List */}
            <div className={`flex-1 pt-3 pb-6 ${isRefreshing ? "animate-in fade-in duration-300" : ""}`}>
                <OrderCard key={refreshKey} onDataChange={handleDataChange} />
            </div>
        </div>
    );
}