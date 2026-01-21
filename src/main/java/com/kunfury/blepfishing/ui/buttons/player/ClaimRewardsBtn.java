package com.kunfury.blepfishing.ui.buttons.player;

import com.kunfury.blepfishing.database.Database;
import com.kunfury.blepfishing.helpers.Formatting;
import com.kunfury.blepfishing.helpers.Utilities;
import com.kunfury.blepfishing.ui.objects.MenuButton;
import com.kunfury.blepfishing.ui.panels.player.PlayerPanel;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ClaimRewardsBtn extends MenuButton {

    public ClaimRewardsBtn(Player player){
        super();
        this.player = player;
    }

@Override
public ItemStack buildItemStack(Player player) {
    ItemStack item = new ItemStack(Material.DIAMOND);
    ItemMeta m = item.getItemMeta();
    if (m == null) return item;

    // Cosmetic glow
    m.addUnsafeEnchantment(Enchantment.FORTUNE, 1);
    m.addItemFlags(ItemFlag.HIDE_ENCHANTS);

    // Display name using Adventure Component
    m.displayName(Formatting.nameHandler("UI.Player.Buttons.Base.Claim Rewards.name"));

    // Lore
    List<String> lore = new ArrayList<>();
    lore.add(Formatting.GetLanguageString("UI.Player.Buttons.Base.Claim Rewards.lore")
            .replace("{amount}",
                    String.valueOf(Database.Rewards.GetTotalRewards(player.getUniqueId().toString()))));
    m.lore(lore);

    item.setItemMeta(m);
    return item;
}


    @Override
    protected void click_left() {
        var rewards = Database.Rewards.GetRewards(player.getUniqueId().toString());

        int undropped = 0;

        for(var reward : rewards){

            if(Utilities.GiveItem(player, reward.Item, false))
                Database.Rewards.Delete(reward.Id);
            else{
                undropped++;;
            }

        }

        if(undropped > 0){
            var msg = Formatting.GetMessagePrefix() + Formatting.GetLanguageString("UI.Player.Buttons.Base.Claim Rewards.name")
                    .replace("{amount}", String.valueOf(undropped));
            player.sendMessage(msg);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, .33f, 1f);

        new PlayerPanel().Show(player);
    }
}
