package com.kongbai.kculauncher.control.keyboard;

import com.kongbai.kculauncher.control.AWTInput;
import com.kongbai.kculauncher.launcher.keycodes.AWTInputEvent;

public class AwtCharSender implements CharacterSenderStrategy {

    private final AWTInput input;

    public AwtCharSender(AWTInput input) {
        this.input = input;
    }

    @Override
    public void sendBackspace() {
        input.sendKey(' ', AWTInputEvent.VK_BACK_SPACE);
    }

    @Override
    public void sendEnter() {
        input.sendKey(' ', AWTInputEvent.VK_ENTER);
    }

    @Override
    public void sendChar(char character) {
        input.sendChar(character);
    }

}
