package com.eu.habbo.habbohotel.users.inventory;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.plugin.events.inventory.InventoryItemAddedEvent;
import com.eu.habbo.plugin.events.inventory.InventoryItemRemovedEvent;
import com.eu.habbo.plugin.events.inventory.InventoryItemsAddedEvent;
import gnu.trove.TCollections;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.THashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.THashSet;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.NoSuchElementException;

@Slf4j
public class ItemsComponent {

    @Getter
    private final TIntObjectMap<HabboItem> items = TCollections.synchronizedMap(new TIntObjectHashMap<>());

    private final HabboInventory inventory;

    public ItemsComponent(HabboInventory inventory, Habbo habbo) {
        this.inventory = inventory;
        this.items.putAll(loadItems(habbo));
    }

    public static THashMap<Integer, HabboItem> loadItems(Habbo habbo) {
        THashMap<Integer, HabboItem> itemsList = new THashMap<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM items WHERE room_id = ? AND user_id = ?")) {
            statement.setInt(1, 0);
            statement.setInt(2, habbo.getHabboInfo().getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    try {
                        HabboItem item = Emulator.getGameEnvironment().getItemManager().loadHabboItem(set);

                        if (item != null) {
                            itemsList.put(set.getInt("id"), item);
                        } else {
                            log.error("Failed to load HabboItem: " + set.getInt("id"));
                        }
                    } catch (SQLException e) {
                        log.error("Caught SQL exception", e);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Caught SQL exception", e);
        }

        return itemsList;
    }

    public void addItem(HabboItem item) {
        if (item == null) {
            return;
        }

        InventoryItemAddedEvent event = new InventoryItemAddedEvent(this.inventory, item);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()) {
            return;
        }

        synchronized (this.items) {
            this.items.put(event.getItem().getId(), event.getItem());
        }
    }

    public void addItems(THashSet<HabboItem> items) {
        InventoryItemsAddedEvent event = new InventoryItemsAddedEvent(this.inventory, items);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()) {
            return;
        }

        synchronized (this.items) {
            for (HabboItem item : event.items) {
                if (item == null) {
                    continue;
                }

                this.items.put(item.getId(), item);
            }
        }
    }

    public HabboItem getHabboItem(int itemId) {
        return this.items.get(Math.abs(itemId));
    }

    public HabboItem getAndRemoveHabboItem(final Item item) {
        final HabboItem[] habboItem = {null};
        synchronized (this.items) {
            this.items.forEachValue(object -> {
                if (object.getBaseItem() == item) {
                    habboItem[0] = object;
                    return false;
                }

                return true;
            });
        }
        this.removeHabboItem(habboItem[0]);
        return habboItem[0];
    }

    public void removeHabboItem(int itemId) {
        this.items.remove(itemId);
    }

    public void removeHabboItem(HabboItem item) {
        InventoryItemRemovedEvent event = new InventoryItemRemovedEvent(this.inventory, item);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()) {
            return;
        }

        synchronized (this.items) {
            this.items.remove(event.getItem().getId());
        }
    }

    public THashSet<HabboItem> getItemsAsValueCollection() {
        THashSet<HabboItem> items = new THashSet<>();
        items.addAll(this.items.valueCollection());

        return items;
    }

    public int itemCount() {
        return this.items.size();
    }

    public void dispose() {
        synchronized (this.items) {
            TIntObjectIterator<HabboItem> items = this.items.iterator();

            if (items == null) {
                log.error("Items is NULL!");
                return;
            }

            if (!this.items.isEmpty()) {
                for (int i = this.items.size(); i-- > 0; ) {
                    try {
                        items.advance();
                    } catch (NoSuchElementException e) {
                        break;
                    }
                    if (items.value().needsUpdate())
                        Emulator.getThreading().run(items.value());
                }
            }

            this.items.clear();
        }
    }
}
