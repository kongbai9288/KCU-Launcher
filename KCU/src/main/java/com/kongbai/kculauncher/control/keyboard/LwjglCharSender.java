package com.kongbai.kculauncher.control.keyboard;

import com.kongbai.kculauncher.control.GameMenu;
import com.kongbai.kculauncher.launcher.keycodes.KCUKeycodes;

public class LwjglCharSender implements CharacterSenderStrategy {

    private final GameMenu gameMenu;

    public LwjglCharSender(GameMenu gameMenu) {
        this.gameMenu = gameMenu;
    }

    @Override
    public void sendBackspace() {
        if (gameMenu.getBridge() != null) {
            gameMenu.getBridge().pushEventKey(KCUKeycodes.KEY_BACKSPACE, '\u0008', true);
            gameMenu.getBridge().pushEventKey(KCUKeycodes.KEY_BACKSPACE, '\u0008', false);
        }
    }

    @Override
    public void sendEnter() {
        gameMenu.getInput().sendKeyEvent(KCUKeycodes.KEY_ENTER, true);
        gameMenu.getInput().sendKeyEvent(KCUKeycodes.KEY_ENTER, false);
    }

    @Override
    public void sendChar(char character) {
        gameMenu.getInput().sendChar(character);
    }
}
