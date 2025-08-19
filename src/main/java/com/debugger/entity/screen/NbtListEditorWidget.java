package com.debugger.entity.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class NbtListEditorWidget extends MultiLineTextFieldWidget {
    private static final int BRACKET_COLOR = 0xFF00FFFF; // 青色括号
    private static final int BRACE_COLOR = 0xFFFFFF00;   // 黄色大括号
    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF; // 默认文本颜色（白色）
    private static final int CURSOR_COLOR = 0xFFFFFFFF;  // 光标颜色
    private static final int SELECTION_COLOR = 0x803333FF; // 文本选择颜色

    public NbtListEditorWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text message) {
        super(textRenderer, x, y, width, height, message);
        this.textRenderer = textRenderer;
        this.setEditable(true);
    }

    @Override
    protected void renderWrappedText(DrawContext context) {
        String text = this.getText();
        int startY = this.getY() + 6;
        int maxWidth = this.getWidth() - 8;

        // 分割文本为多行（按换行符）
        List<String> lines = new ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            int lineEnd = text.indexOf('\n', pos);
            if (lineEnd == -1) lineEnd = text.length();
            String line = text.substring(pos, lineEnd);
            lines.add(line);
            pos = lineEnd + 1;
        }

        // 渲染每一行（带括号高亮）
        int yPos = startY;
        for (String line : lines) {
            int xPos = this.getX() + 4;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                String charStr = String.valueOf(c);
                int color = DEFAULT_TEXT_COLOR;

                // 高亮括号
                if (c == '[' || c == ']') {
                    color = BRACKET_COLOR;
                } else if (c == '{' || c == '}') {
                    color = BRACE_COLOR;
                }

                context.drawText(textRenderer, charStr, xPos, yPos, color, false);
                xPos += textRenderer.getWidth(charStr);
            }
            yPos += textRenderer.fontHeight;
        }

        // 渲染光标
        renderCursor(context);
    }

    private void renderCursor(DrawContext context) {
        if (!this.isFocused()) return;

        String text = this.getText();
        int cursorPos = this.getCursor();

        // 计算光标所在行
        String textBeforeCursor = text.substring(0, cursorPos);
        String[] lines = textBeforeCursor.split("\n", -1);
        int currentLine = lines.length - 1;

        // 计算光标X位置（当前行的宽度）
        String currentLineText = lines[currentLine];
        int cursorX = this.getX() + 4 + textRenderer.getWidth(currentLineText);

        // 计算光标Y位置
        int cursorY = this.getY() + 6 + (currentLine * textRenderer.fontHeight);

        // 绘制光标
        context.fill(cursorX, cursorY, cursorX + 1, cursorY + textRenderer.fontHeight, CURSOR_COLOR);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible) {
            return false;
        }

        boolean clicked = mouseX >= this.getX() &&
                mouseX < this.getX() + this.width &&
                mouseY >= this.getY() &&
                mouseY < this.getY() + this.height;

        if (clicked) {
            this.setFocused(true);
            this.setCursorFromMousePos(mouseX, mouseY);
            return true;
        }
        return false;
    }

    private void setCursorFromMousePos(double mouseX, double mouseY) {
        String text = this.getText();
        int relativeX = (int)mouseX - this.getX() - 4;
        int relativeY = (int)mouseY - this.getY() - 6;

        // 计算点击的行号
        int lineNumber = Math.max(0, relativeY / this.textRenderer.fontHeight);
        String[] lines = text.split("\n", -1);

        if (lineNumber >= lines.length) {
            this.setCursor(text.length());
            return;
        }

        // 在当前行中找到点击位置
        String line = lines[lineNumber];
        int linePos = 0;
        int accumulatedWidth = 0;

        while (linePos < line.length()) {
            int charWidth = textRenderer.getWidth(line.substring(linePos, linePos + 1));
            if (accumulatedWidth + charWidth / 2 > relativeX) {
                break;
            }
            accumulatedWidth += charWidth;
            linePos++;
        }

        // 计算全局光标位置
        int globalPos = 0;
        for (int i = 0; i < lineNumber; i++) {
            globalPos += lines[i].length() + 1; // +1 for newline
        }
        globalPos += linePos;

        this.setCursor(globalPos);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP:
                moveCursorUp();
                return true;
            case GLFW.GLFW_KEY_DOWN:
                moveCursorDown();
                return true;
            case GLFW.GLFW_KEY_ENTER:
                this.write("\n");
                return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveCursorUp() {
        int cursorPos = this.getCursor();
        String text = this.getText();

        if (text.isEmpty()) return;

        int lineStart = text.lastIndexOf('\n', cursorPos - 1);
        if (lineStart == -1) {
            this.setCursor(0);
            return;
        }

        int prevLineStart = text.lastIndexOf('\n', lineStart - 1);
        int lineOffset = cursorPos - lineStart - 1;

        if (prevLineStart == -1) {
            this.setCursor(Math.min(lineOffset, lineStart));
        } else {
            String prevLine = text.substring(prevLineStart + 1, lineStart);
            int newPos = prevLineStart + 1 + Math.min(lineOffset, prevLine.length());
            this.setCursor(newPos);
        }
    }

    private void moveCursorDown() {
        int cursorPos = this.getCursor();
        String text = this.getText();

        if (text.isEmpty()) return;

        int lineEnd = text.indexOf('\n', cursorPos);
        if (lineEnd == -1) lineEnd = text.length();

        int nextLineStart = lineEnd + 1;
        if (nextLineStart >= text.length()) {
            this.setCursor(text.length());
            return;
        }

        int nextLineEnd = text.indexOf('\n', nextLineStart);
        if (nextLineEnd == -1) nextLineEnd = text.length();

        int lineOffset = cursorPos - (text.lastIndexOf('\n', cursorPos) + 1);
        String nextLine = text.substring(nextLineStart, nextLineEnd);
        int newPos = nextLineStart + Math.min(lineOffset, nextLine.length());

        this.setCursor(newPos);
    }
}