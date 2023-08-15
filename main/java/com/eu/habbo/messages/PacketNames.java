package com.eu.habbo.messages;

import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.outgoing.Outgoing;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;

@Slf4j
public class PacketNames {


    private final HashMap<Integer, String> incoming;
    private final HashMap<Integer, String> outgoing;

    public PacketNames() {
        this.incoming = new HashMap<>();
        this.outgoing = new HashMap<>();
    }

    private boolean initialized = false;

    /**
     * Initializes the packet names by fetching names from the Incoming and Outgoing classes.
     * This method should only be called once per instance.
     */
    public void initialize() {
        if (initialized) {
            throw new IllegalStateException("PacketNames has already been initialized.");
        }

        PacketNames.getNames(Incoming.class, this.incoming);
        PacketNames.getNames(Outgoing.class, this.outgoing);

        initialized = true;
    }


    public String getIncomingName(int key) {
        return this.incoming.getOrDefault(key, "Unknown");
    }

    public String getOutgoingName(int key) {
        return this.outgoing.getOrDefault(key, "Unknown");
    }

    private static void getNames(Class<?> clazz, HashMap<Integer, String> target) {
        for (Field field : clazz.getFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers) && field.getType() == int.class) {
                try {
                    int packetId = field.getInt(null);
                    if (packetId > 0) {
                        if (target.containsKey(packetId)) {
                            log.warn("Duplicate packet id found {} for {}.", packetId, clazz.getSimpleName());
                            continue;
                        }

                        target.put(packetId, field.getName());
                    }
                } catch (IllegalAccessException e) {
                    log.error("Failed to read field integer.", e);
                }
            }
        }
    }

}
