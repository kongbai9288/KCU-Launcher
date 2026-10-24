package com.kongbai.kculauncher.control.gamepad

class GamepadButton : GamepadEmulatedButton() {
    var isToggleable: Boolean = false
    private var isToggled = false


    override fun onDownStateChanged(isDown: Boolean) {
        if (isToggleable) {
            if (!isDown) return
            isToggled = !isToggled
            keycodes.forEach {
                Gamepad.getKCUInput()?.sendKeyEvent(it, isToggled)
            }
            return
        }
        super.onDownStateChanged(isDown)
    }

    override fun resetButtonState() {
        if(!isDown && isToggleable) {
            keycodes.forEach {
                Gamepad.getKCUInput()?.sendKeyEvent(it, false)
            }
            isToggled = false;
        } else {
            super.resetButtonState();
        }
    }
}
