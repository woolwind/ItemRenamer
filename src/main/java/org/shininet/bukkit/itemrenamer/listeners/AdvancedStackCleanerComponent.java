package org.shininet.bukkit.itemrenamer.listeners;

import java.io.DataInputStream;
import java.io.IOException;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.shininet.bukkit.itemrenamer.AbstractRenameProcessor;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketAdapter.AdapterParameteters;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.comphenix.protocol.utility.StreamSerializer;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;

/**
 * Represents an item stack unprocessor or cleaner that is required in Minecraft 1.6.1 and above.
 * @author Kristian
 */
class AdvancedStackCleanerComponent extends BasicStackCleanerComponent {
	/**
	 * Stores the version after which the NBT fix is mandatory.
	 */
	private static final MinecraftVersion REQUIRED_VERSION = new MinecraftVersion(1, 6, 1);
	
	public AdvancedStackCleanerComponent(@Nonnull AbstractRenameProcessor processor, @Nonnull ProtocolManager manager) {
		super(processor, manager);
	}

	/**
	 * Determine if this fix is required.
	 * @return TRUE if it is, FALSE otherwise.
	 */
	public static boolean isRequired() {
		MinecraftVersion current = new MinecraftVersion(Bukkit.getServer());
		
		// Only enable this fix for Minecraft 1.6.1 and later
		return REQUIRED_VERSION.compareTo(current) <= 0;
	}
	
	@Override
	protected AdapterParameteters adapterBuilder(Plugin plugin) {
		// Add the ability to intercept the raw packet data
		return super.adapterBuilder(plugin).optionIntercept();
	}
	
	@Override
	protected void unprocessFieldStack(PacketEvent event, ItemStack modified) {
		DataInputStream input = event.getNetworkMarker().getInputStream();

		// Skip simulated packets
		if (input == null)
			return;

		try {
			// Read slot
			if (event.getPacketType() == PacketType.Play.Client.SET_CREATIVE_SLOT)
				input.skipBytes(2);
			else if (event.getPacketType() == PacketType.Play.Client.BLOCK_PLACE)
				input.skipBytes(10);
			
			ItemStack stack = readItemStack(input, new StreamSerializer());

			// Now we can properly unprocess it
			processor.unprocess(stack);

			// And write it back
			event.getPacket().getItemModifier().write(0, stack);

		} catch (IOException e) {
			// Just let ProtocolLib handle it
			throw new RuntimeException("Cannot undo NBT scrubber.", e);
		}
	}
	
	/**
	 * Read an ItemStack from a input stream without "scrubbing" the NBT content.
	 * @param input - the input stream.
	 * @param serializer - methods for serializing Minecraft object.
	 * @return The deserialized item stack.
	 * @throws IOException If anything went wrong.
	 */
	private ItemStack readItemStack(DataInputStream input, StreamSerializer serializer) throws IOException {
		ItemStack result = null;
		short type = input.readShort();

		if (type >= 0) {
			byte amount = input.readByte();
			short damage = input.readShort();

			result = new ItemStack(type, amount, damage);
			NbtCompound tag = serializer.deserializeCompound(input);

			if (tag != null) {
				result = MinecraftReflection.getBukkitItemStack(result);
				NbtFactory.setItemTag(result, tag);
			}
		}
		return result;
	}
}
