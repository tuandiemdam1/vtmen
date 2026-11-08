"use client";

import { useState, useRef, useEffect } from "react";
import { ChevronDown, X, MapPin, Check } from "lucide-react";
import { Input } from "@/components/ui/input";
import type { MapLocationPoint } from "@/lib/api";

export function LocationPicker({
    locations,
    loading,
    value,
    onChange,
}: {
    locations: MapLocationPoint[];
    loading?: boolean;
    value: string;
    onChange: (address: string) => void;
}) {
    const [query, setQuery] = useState("");
    const [open, setOpen] = useState(false);
    const containerRef = useRef<HTMLDivElement>(null);

    const selected = locations.find((l) => l.address === value);

    const filtered = locations.filter((l) => {
        if (!query) return true;
        const q = query.toLowerCase();
        const searchStr = `${l.name || ""} ${l.address || ""} ${l.dockName || ""} ${l.dockShowName || ""}`.toLowerCase();
        return searchStr.includes(q);
    });

    useEffect(() => {
        function handleClickOutside(e: Event) {
            if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
                setOpen(false);
                setQuery("");
            }
        }
        document.addEventListener("mousedown", handleClickOutside);
        document.addEventListener("touchstart", handleClickOutside);
        return () => {
            document.removeEventListener("mousedown", handleClickOutside);
            document.removeEventListener("touchstart", handleClickOutside);
        };
    }, []);

    function handleSelect(address: string) {
        onChange(address);
        setOpen(false);
        setQuery("");
    }

    function handleClear(e: React.MouseEvent) {
        e.stopPropagation();
        onChange("");
        setQuery("");
    }

    return (
        <div ref={containerRef} className="space-y-2">
            <div className="relative">
                <div
                    className="flex h-11 w-full cursor-pointer items-center justify-between rounded-xl border border-border/60 bg-muted/30 px-3 text-sm transition-all hover:bg-muted/50 active:scale-[0.99]"
                    onClick={() => {
                        setOpen((o) => !o);
                        setQuery("");
                    }}
                >
                    <div className="flex items-center gap-2 truncate">
                        <MapPin className="h-4 w-4 shrink-0 text-primary" />
                        {loading ? (
                            <span className="text-muted-foreground">Đang tải địa điểm…</span>
                        ) : selected ? (
                            <span className="truncate font-medium text-foreground">
                                {selected.dockShowName && selected.dockName
                                    ? `${selected.dockShowName} - ${selected.dockName}`
                                    : (selected.dockShowName || selected.dockName || selected.address)}
                            </span>
                        ) : (
                            <span className="text-muted-foreground">Chọn điểm giao hàng...</span>
                        )}
                    </div>
                    <div className="ml-2 flex shrink-0 items-center gap-1">
                        {value && (
                            <button
                                type="button"
                                onClick={handleClear}
                                className="rounded p-1 text-muted-foreground hover:text-foreground active:scale-95"
                                aria-label="Xóa địa điểm đã chọn"
                            >
                                <X className="h-3.5 w-3.5" />
                            </button>
                        )}
                        <ChevronDown className={`h-4 w-4 text-muted-foreground transition-transform duration-200 ${open ? "rotate-180" : ""}`} />
                    </div>
                </div>

                {open && !loading && (
                    <div className="absolute z-50 mt-1.5 w-full overflow-hidden rounded-xl border border-border/60 bg-popover shadow-xl animate-in fade-in-0 zoom-in-95">
                        {/* Only show search input if more than 4 locations */}
                        {locations.length > 4 && (
                            <div className="border-b border-border/40 p-2">
                                <Input
                                    placeholder="Tìm theo tên điểm..."
                                    value={query}
                                    onChange={(e) => setQuery(e.target.value)}
                                    className="h-9 rounded-lg border-border/40 bg-muted/40 text-sm"
                                    onClick={(e) => e.stopPropagation()}
                                />
                            </div>
                        )}
                        <ul className="max-h-56 overflow-y-auto p-1.5 custom-scrollbar">
                            {filtered.length === 0 ? (
                                <li className="py-4 text-center text-sm text-muted-foreground">
                                    Không tìm thấy địa điểm
                                </li>
                            ) : (
                                filtered.map((loc) => {
                                    const isChosen = value === loc.address;
                                    const title = loc.dockShowName && loc.dockName
                                        ? `${loc.dockShowName} - ${loc.dockName}`
                                        : (loc.dockShowName || loc.dockName || loc.address);
                                    return (
                                        <li
                                            key={loc.id}
                                            onMouseDown={(e) => {
                                                e.preventDefault();
                                                handleSelect(loc.address);
                                            }}
                                            onClick={() => handleSelect(loc.address)}
                                            className={`flex cursor-pointer items-center justify-between rounded-lg px-3 py-2.5 text-sm transition-colors active:bg-primary/10 ${
                                                isChosen
                                                    ? "bg-primary/10 font-semibold text-primary"
                                                    : "hover:bg-accent text-foreground"
                                            }`}
                                        >
                                            <div className="flex flex-col truncate pr-2">
                                                <span className="truncate leading-snug">{title}</span>
                                                {loc.name && loc.name !== title && (
                                                    <span className="text-xs text-muted-foreground leading-snug">{loc.name}</span>
                                                )}
                                            </div>
                                            {isChosen && <Check className="h-4 w-4 shrink-0 text-primary" />}
                                        </li>
                                    );
                                })
                            )}
                        </ul>
                    </div>
                )}
            </div>

            {/* Quick-select chips for mobile: 1-tap selection */}
            {locations.length > 0 && (
                <div className="flex flex-wrap items-center gap-1.5 pt-0.5">
                    <span className="text-[11px] font-medium text-muted-foreground mr-1">Điểm nhanh:</span>
                    {locations.slice(0, 6).map((loc) => {
                        const isChosen = value === loc.address;
                        const label = loc.dockShowName || loc.name || loc.address;
                        return (
                            <button
                                key={loc.id}
                                type="button"
                                onClick={() => onChange(loc.address)}
                                className={`inline-flex items-center gap-1 rounded-lg px-2.5 py-1 text-xs transition-all active:scale-95 ${
                                    isChosen
                                        ? "bg-primary text-primary-foreground font-semibold shadow-xs ring-1 ring-primary"
                                        : "border border-border/70 bg-muted/40 text-foreground hover:bg-muted active:bg-muted/80"
                                }`}
                            >
                                <MapPin className={`h-3 w-3 shrink-0 ${isChosen ? "text-primary-foreground" : "text-primary"}`} />
                                <span className="truncate max-w-[130px]">{label}</span>
                            </button>
                        );
                    })}
                </div>
            )}
        </div>
    );
}
