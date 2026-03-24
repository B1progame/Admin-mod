package com.b1progame.adminmod.gui;

import com.b1progame.adminmod.config.ConfigManager;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GuiItemFactory {
    private GuiItemFactory() {
    }

    public static ItemStack button(Item item, Text name, List<Text> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME, name);
        if (!lore.isEmpty()) {
            stack.set(DataComponentTypes.LORE, new LoreComponent(new ArrayList<>(lore)));
        }
        return stack;
    }

    public static ItemStack button(Item item, String name, Formatting color, List<String> lore) {
        List<Text> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Text.literal(line));
        }
        return button(item, Text.literal(name).formatted(color), lines);
    }

    public static ItemStack filler() {
        return button(Items.GRAY_STAINED_GLASS_PANE, Text.literal(" "), List.of());
    }

    public static ItemStack separator() {
        return button(Items.BLUE_STAINED_GLASS_PANE, Text.literal(" "), List.of());
    }

    public static ItemStack playerHead(UUID uuid, String name, List<String> lore) {
        return playerHead(new GameProfile(uuid, name), name, lore);
    }

    public static ItemStack playerHead(GameProfile profile, String fallbackName, List<String> lore) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        GameProfile resolvedProfile = profile == null
                ? new GameProfile(UUID.randomUUID(), fallbackName)
                : profile;
        String displayName = resolvedProfile.name() == null || resolvedProfile.name().isBlank()
                ? fallbackName
                : resolvedProfile.name();
        if (hasTextures(resolvedProfile)) {
            stack.set(DataComponentTypes.PROFILE, ProfileComponent.ofStatic(resolvedProfile));
        } else if (resolvedProfile.id() != null) {
            stack.set(DataComponentTypes.PROFILE, ProfileComponent.ofDynamic(resolvedProfile.id()));
        } else if (displayName != null && !displayName.isBlank()) {
            stack.set(DataComponentTypes.PROFILE, ProfileComponent.ofDynamic(displayName));
        } else {
            stack.set(DataComponentTypes.PROFILE, ProfileComponent.ofStatic(resolvedProfile));
        }
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName).formatted(Formatting.GOLD));
        if (!lore.isEmpty()) {
            List<Text> textLore = new ArrayList<>();
            for (String line : lore) {
                textLore.add(Text.literal(line));
            }
            stack.set(DataComponentTypes.LORE, new LoreComponent(textLore));
        }
        return stack;
    }

    private static boolean hasTextures(GameProfile profile) {
        return profile != null
                && profile.properties() != null
                && profile.properties().containsKey("textures")
                && !profile.properties().get("textures").isEmpty();
    }

    public static ItemStack backButton(ConfigManager configManager) {
        String texture = configManager.get().gui.back_button_texture;
        if (texture != null && !texture.isBlank()) {
            GameProfile profile = new GameProfile(UUID.randomUUID(), "back");
            profile.properties().put("textures", new Property("textures", texture));
            ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
            stack.set(DataComponentTypes.PROFILE, ProfileComponent.ofStatic(profile));
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Back").formatted(Formatting.RED));
            return stack;
        }
        return button(Items.ARROW, Text.literal("Back").formatted(Formatting.RED), List.of());
    }
}
