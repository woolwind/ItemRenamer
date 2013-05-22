package org.shininet.bukkit.itemrenamer.configuration;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.configuration.ConfigurationSection;
import org.shininet.bukkit.itemrenamer.serialization.DamageSerializer;

import com.comphenix.protocol.reflect.FieldUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Store the rename packs from a configuration file.
 * 
 * @author Kristian
 */
public class RenameConfiguration {
	private final ConfigurationSection section;
	
	// Group Manager sucks
	private static Field internalMap;
	
	// Store of every loaded lookup
	private final Map<String, Map<Integer, DamageLookup>> memoryLookup = Maps.newHashMap();
	
	// How many times this configuration has changed
	private int modCount;
	
	public RenameConfiguration(ConfigurationSection section) {
		this.section = section;
	}

	/**
	 * Retrieve a damage lookup object from a given name pack and item ID.
	 * <p>
	 * The pack must exist. 
	 * @param pack - the name pack. 
	 * @param itemID - the item ID.
	 * @return The damage lookup, or NULL if the item ID has not been registered.
	 */
	public DamageLookup getLookup(String pack, int itemID) {
		Map<Integer, DamageLookup> itemLookup = loadPack(pack);

		if (itemLookup != null) {
			return itemLookup.get(itemID);
		} else {
			throw new IllegalArgumentException("Pack " + pack + " doesn't exist.");
		}
	}
	
	private ConfigurationSection getSection(ConfigurationSection parent, String key) {
		ConfigurationSection result = parent.getConfigurationSection(key);
		
		// What the hell?
		if (result == parent) {
			if (internalMap == null)
				internalMap = FieldUtils.getField(result.getClass(), "map", true);
			try {
				@SuppressWarnings("unchecked")
				Map<String, Object> map = (Map<String, Object>) internalMap.get(parent);
				Object raw = map != null ? map.get(key) : null;
				
				// Neat
				if (raw instanceof ConfigurationSection)
					return (ConfigurationSection) raw;
			} catch (Exception e) {
				throw new RuntimeException("GroupMananger hack failed!", e);
			}
			// Failure
			return null;
		}
		return result;
	}
	
	/**
	 * Load a given item name pack from the configuration file.
	 * @param pack - the pack to load.
	 * @return A map representation of this pack.
	 */
	private Map<Integer, DamageLookup> loadPack(String pack) {
		Map<Integer, DamageLookup> itemLookup = memoryLookup.get(pack);
		
		// Initialize item lookup
		if (itemLookup == null) {
			ConfigurationSection items = getSection(section, pack);
			
			if (items != null) {
				memoryLookup.put(pack, itemLookup = Maps.newHashMap());
				
				for (String key : items.getKeys(false)) {
					Integer id = Integer.parseInt(key);
					DamageSerializer serializer = new DamageSerializer(getSection(items, key));	
					DamageLookup damage = new MemoryDamageLookup();
					
					// Load and save
					serializer.readLookup(damage);
					itemLookup.put(id, damage);
				}
			} else {
				return null;
			}
		}
		return itemLookup;
	}
	
	/**
	 * Determine if the given pack exists.
	 * @param pack - the pack to lookup.
	 * @return TRUE if it does, FALSE otherwise.
	 */
	public boolean hasPack(String pack) {
		return loadPack(pack) != null;
	}

	/**
	 * Create a new damage lookup, or load the existing damage value if it exists.
	 * @param pack - package it belongs to.
	 * @param itemID - item ID.
	 * @return Existing damage lookup, or a new one if it doesn't exist.
	 */
	public DamageLookup createLookup(String pack, int itemID) {
		Map<Integer, DamageLookup> itemLookup = loadOrCreatePack(pack);
		
		// Same thing for the lookup
		DamageLookup lookup = itemLookup.get(itemID);
		
		if (lookup == null) {
			modCount++;
			itemLookup.put(itemID, lookup = new MemoryDamageLookup());
		}
		return lookup;
	}
	
	/**
	 * Delete the pack with the given name.
	 * @param pack - the pack to remove.
	 * @return TRUE if a pack was removed, FALSE otherwise.
	 */
	public boolean removePack(String pack) {
		modCount++;
		return memoryLookup.remove(pack) != null;
	}
	
	/**
	 * Construct a new item pack.
	 * @param pack - name of the pack to construct.
	 * @return TRUE if this pack was constructed, FALSE if a pack by this name already exists.
	 */
	public boolean createPack(String pack) {
		if (loadPack(pack) == null) {
			loadOrCreatePack(pack);
			return true;
		}
		return false;
	}
	
	/**
	 * Load or create a new pack.
	 * @param pack - name of the pack to create.
	 * @return The existing pack, or a new one.
	 */
	private Map<Integer, DamageLookup> loadOrCreatePack(String pack) {
		Map<Integer, DamageLookup> itemLookup = loadPack(pack);
		
		// Create a new if we need to
		if (itemLookup == null) {
			modCount++;
			memoryLookup.put(pack, itemLookup = Maps.newHashMap());
		}
		return itemLookup;
	}
	
	/**
	 * Save the given name pack to the configuration file.
	 * @param pack - name pack.
	 */
	public void saveLookup(String pack) {
		Map<Integer, DamageLookup> itemLookup = memoryLookup.get(pack);
		
		if (itemLookup != null) {
			// Write all the stored damage lookups
			for (Entry<Integer, DamageLookup> entry : itemLookup.entrySet()) {
				DamageSerializer serializer = new DamageSerializer(section.createSection(pack + "." + entry.getKey()));
				serializer.writeLookup(entry.getValue());
			}
			
		} else {
			throw new IllegalArgumentException("Cannot save " + pack + ": It doesn't exist.");
		}
	}
	
	/**
	 * Determine if this configuration has changed.
	 * @return TRUE if it has, FALSE otherwise.
	 */
	public int getModificationCount() {
		int totalCount = modCount;
		
		for (Entry<String, Map<Integer, DamageLookup>> packEntry : memoryLookup.entrySet()) {
			for (DamageLookup lookup : packEntry.getValue().values()) {
				if (lookup.getModificationCount() > 0) {
					totalCount += lookup.getModificationCount();
				}
			}
		}
		return totalCount;
	}
	
	/**
	 * Save every name pack and every setting.
	 */
	public void saveAll() {
		// Reset everything first
		for (String key : Lists.newArrayList(section.getKeys(false))) {
			section.set(key, null);
		}
		
		for (String pack : memoryLookup.keySet()) {
			saveLookup(pack);
		}
	}
}
