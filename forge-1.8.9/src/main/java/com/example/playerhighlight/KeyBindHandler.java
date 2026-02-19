package com.example.playerhighlight;

import org.lwjgl.input.Keyboard;

/**
 * 按键绑定处理器
 *
 * TAB 键切换玩家高亮（按住生效）
 */
public class KeyBindHandler {

    private KeyBindHandler() {}

    /**
     * 每 tick 调用，检测 TAB 键状态
     */
    public static void tick() {
        boolean tabPressed = Keyboard.isKeyDown(Keyboard.KEY_TAB);
        PlayerHighlightMod.setHighlightEnabled(tabPressed);
    }
}
