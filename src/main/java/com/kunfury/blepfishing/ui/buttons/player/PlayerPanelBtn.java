package com.kunfury.blepfishing.ui.buttons.player;

import com.kunfury.blepfishing.helpers.Formatting;
import com.kunfury.blepfishing.ui.objects.MenuButton;
import com.kunfury.blepfishing.ui.panels.player.PlayerPanel;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;


public class PlayerPanelBtn extends MenuButton {

    @Override
    public ItemStack buildItemStack(Player player) {
        // Create the base item
        ItemStack item = new ItemStack(Material.SALMON);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item; // safety check

        // Set the display name
        meta.displayName(Formatting.nameHandler("UI.Player.Buttons.panel"));

        // Apply cosmetic glow safely
        meta.addUnsafeEnchantment(Enchantment.FORTUNE, 1);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // Apply meta back to item
        item.setItemMeta(meta);

        return item;
    }

    @Override
    protected void click_left() {
        new PlayerPanel().Show(player);
    }
}
