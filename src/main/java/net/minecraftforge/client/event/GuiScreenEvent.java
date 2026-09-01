package net.minecraftforge.client.event;

import net.minecraft.client.gui.GuiScreen;


public class GuiScreenEvent {

    public GuiScreen gui;

    public GuiScreen getGui() {
        return gui;
    }

    public static class DrawScreenEvent extends GuiScreenEvent {
        public static class Post extends DrawScreenEvent {
        }
    }
}
