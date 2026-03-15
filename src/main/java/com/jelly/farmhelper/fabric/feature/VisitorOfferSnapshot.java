package com.jelly.farmhelper.fabric.feature;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VisitorOfferSnapshot {
    public boolean visitorScreenOpen;
    public boolean inventoryLoaded;
    public String inventoryName = "";
    public int npcSlot = -1;
    public String npcName = "";
    public int npcColorRgb = -1;
    public final List<String> npcLore = new ArrayList<>();
    public int acceptOfferSlot = -1;
    public String acceptOfferName = "";
    public final List<String> acceptOfferLore = new ArrayList<>();
    public final Map<String, Integer> inventoryCounts = new LinkedHashMap<>();
    public final List<Integer> hotbarCompactorSlots = new ArrayList<>();
    public int emptyInventorySlots;

    public void clear() {
        visitorScreenOpen = false;
        inventoryLoaded = false;
        inventoryName = "";
        npcSlot = -1;
        npcName = "";
        npcColorRgb = -1;
        npcLore.clear();
        acceptOfferSlot = -1;
        acceptOfferName = "";
        acceptOfferLore.clear();
        inventoryCounts.clear();
        hotbarCompactorSlots.clear();
        emptyInventorySlots = 0;
    }

    public VisitorOfferSnapshot copy() {
        VisitorOfferSnapshot copy = new VisitorOfferSnapshot();
        copy.visitorScreenOpen = visitorScreenOpen;
        copy.inventoryLoaded = inventoryLoaded;
        copy.inventoryName = inventoryName;
        copy.npcSlot = npcSlot;
        copy.npcName = npcName;
        copy.npcColorRgb = npcColorRgb;
        copy.npcLore.addAll(npcLore);
        copy.acceptOfferSlot = acceptOfferSlot;
        copy.acceptOfferName = acceptOfferName;
        copy.acceptOfferLore.addAll(acceptOfferLore);
        copy.inventoryCounts.putAll(inventoryCounts);
        copy.hotbarCompactorSlots.addAll(hotbarCompactorSlots);
        copy.emptyInventorySlots = emptyInventorySlots;
        return copy;
    }
}
