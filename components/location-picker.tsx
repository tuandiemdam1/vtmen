"use client";

import { useState, useRef, useEffect } from "react";
import { ChevronDown, X } from "lucide-react";
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
        function handleClickOutside(e: MouseEvent) {
            if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
                setOpen(false);
                setQuery("");
            }
        }
        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
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
        <div ref={containerRef} className="relative">
            <div
                className="flex h-11 w-full cursor-pointer items-center justify-between rounded-xl border border-border/60 bg-muted/30 px-3 text-sm transition-all hover:bg-muted/50"
                onClick={() => {
                    setOpen((o) => !o);
                    setQuery("");
                }}
            >
                {loading ? (
                    <span className="text-muted-foreground">Đang tải địa điểm…</span>
                ) : selected ? (
                    <span className="truncate text-foreground">
                        {selected.dockShowName && selected.dockName ? `${selected.dockShowName} - ${selected.dockName}` : (selected.dockShowName || selected.dockName || selected.address)}
                    </span>
                ) : (
                    <span className="text-muted-foreground">Tìm theo tên hoặc địa chỉ...</span>
                )}
                <div className="ml-2 flex shrink-0 items-center gap-1">
                    {value && (
                        <button
                            type="button"
                            onClick={handleClear}
                            className="rounded p-0.5 text-muted-foreground hover:text-foreground"
                        >
                            <X className="h-3.5 w-3.5" />
                        </button>
                    )}
                    <ChevronDown className={`h-4 w-4 text-muted-foreground transition-transform ${open ? "rotate-180" : ""}`} />
                </div>
            </div>

            {open && !loading && (
                <div className="absolute z-50 mt-1 w-full overflow-hidden rounded-xl border border-border/60 bg-popover shadow-lg">
                    <div className="border-b border-border/40 p-2">
                        <Input
                            autoFocus
                            placeholder="Tìm kiếm..."
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                            className="h-8 rounded-lg border-border/40 bg-muted/30 text-sm"
                            onClick={(e) => e.stopPropagation()}
                        />
                    </div>
                    <ul className="max-h-80 overflow-y-auto p-1">
                        {filtered.length === 0 ? (
                            <li className="py-4 text-center text-sm text-muted-foreground">
                                Không tìm thấy địa điểm
                            </li>
                        ) : (
                            filtered.map((loc) => (
                                <li
                                    key={loc.id}
                                    onMouseDown={(e) => {
                                        e.preventDefault();
                                        handleSelect(loc.address);
                                    }}
                                    className={`flex cursor-pointer flex-col rounded-lg px-3 py-2 text-sm hover:bg-accent hover:text-accent-foreground ${
                                        value === loc.address ? "bg-accent/50" : ""
                                    }`}
                                >
                                    <span className="font-medium leading-snug">
                                        {loc.dockShowName && loc.dockName ? `${loc.dockShowName} - ${loc.dockName}` : (loc.dockShowName || loc.dockName || loc.address)}
                                    </span>
                                    <span className="text-xs text-muted-foreground leading-snug">{loc.name}</span>
                                </li>
                            ))
                        )}
                    </ul>
                </div>
            )}
        </div>
    );
}
