package com.debugger.entity.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

public class MultiLineTextFieldWidget extends TextFieldWidget {
    protected static final int TEXT_COLOR = 0xE0E0E0;
    protected static final int BACKGROUND_COLOR = 0xFF1E1E1E;
    TextRenderer textRenderer;
    private boolean wrapText = true;

    public MultiLineTextFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text text) {
        super(textRenderer, x, y, width, height, text);
        this.setMaxLength(Integer.MAX_VALUE);
        this.textRenderer = textRenderer;
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.wrapText) {
            renderWrappedText(context);
        } else {
            super.renderButton(context, mouseX, mouseY, delta);
        }
    }

    protected void renderWrappedText(DrawContext context) {
        // 基础渲染实现
        String text = this.getText();
        List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(text), this.getWidth() - 8);

        int yPos = this.getY() + 6;
        for (OrderedText line : lines) {
            context.drawText(this.textRenderer, line, this.getX() + 4, yPos, TEXT_COLOR, false);
            yPos += this.textRenderer.fontHeight;
        }
    }

    public void setWrapText(boolean wrapText) {
        this.wrapText = wrapText;
    }
}