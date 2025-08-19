package com.debugger.entity.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import java.util.List;
import java.util.function.Consumer;

public class DropdownWidget extends ClickableWidget {
    private final List<Option> options;
    private int selectedIndex;
    private boolean expanded = false;
    private Consumer<Option> changeListener;
    private final Screen parentScreen;

    public DropdownWidget(Screen parent, int x, int y, int width, int height,
                          Text message, List<Option> options, int defaultIndex) {
        super(x, y, width, height, message);
        this.parentScreen = parent;
        this.options = options;
        this.selectedIndex = defaultIndex;
    }

    public void setChangedListener(Consumer<Option> listener) {
        this.changeListener = listener;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public boolean parentMouseClicked(double mouseX, double mouseY, int button) {
        if (expanded && !isMouseOver(mouseX, mouseY)) {
            expanded = false;
            return true;
        }
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!this.visible) return;

        // 背景
        context.fill(getX(), getY(), getX() + width, getY() + height, 0xFF333333);

        // 边框
        context.fill(getX(), getY(), getX() + width, getY() + 1, 0xFF666666);
        context.fill(getX(), getY() + height - 1, getX() + width, getY() + height, 0xFF666666);
        context.fill(getX(), getY(), getX() + 1, getY() + height, 0xFF666666);
        context.fill(getX() + width - 1, getY(), getX() + width, getY() + height, 0xFF666666);

        // 当前选中的文本
        Option selected = options.get(selectedIndex);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, selected.text(),
                getX() + 5, getY() + (height - 8) / 2, 0xFFFFFF);

        // 下拉箭头
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("▼"),
                getX() + width - 15, getY() + (height - 8) / 2,
                isHovered() ? 0xFFFFA0 : 0xFFFFFF);

        // 下拉列表
        if (expanded) {
            int dropdownHeight = options.size() * (height + 2);
            // 下拉框背景
            context.fill(getX(), getY() + height + 1,
                    getX() + width, getY() + height + 1 + dropdownHeight, 0xFF222222);
            // 下拉框外边框
            context.fill(getX(), getY() + height, getX() + width, getY() + height + 1, 0xFF666666);
            context.fill(getX(), getY() + height + 1 + dropdownHeight,
                    getX() + width, getY() + height + 2 + dropdownHeight, 0xFF666666);
            context.fill(getX(), getY() + height, getX() + 1, getY() + height + 2 + dropdownHeight, 0xFF666666);
            context.fill(getX() + width - 1, getY() + height,
                    getX() + width, getY() + height + 2 + dropdownHeight, 0xFF666666);

            for (int i = 0; i < options.size(); i++) {
                int optionY = getY() + height + 1 + i * (height + 2);
                boolean hovered = isMouseOver(mouseX, mouseY) &&
                        mouseY >= optionY && mouseY < optionY + height;

                // 选项背景
                context.fill(getX() + 1, optionY,
                        getX() + width - 1, optionY + height,
                        hovered ? 0xFF444444 : 0xFF333333);

                // 选项上边线（除第一个选项外）
                if (i > 0) {
                    context.fill(getX() + 1, optionY - 1,
                            getX() + width - 1, optionY, 0xFF555555);
                }

                // 选项文本
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        options.get(i).text(),
                        getX() + 5, optionY + (height - 8) / 2,
                        0xFFFFFF);
            }
        }
    }

    @Override
    protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {

    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible) return false;

        if (expanded) {
            // 检查是否点击了选项
            for (int i = 0; i < options.size(); i++) {
                int optionY = getY() + height + 1 + i * (height + 2);
                if (mouseY >= optionY && mouseY < optionY + height) {
                    selectedIndex = i;
                    if (changeListener != null) {
                        changeListener.accept(options.get(selectedIndex));
                    }
                    expanded = false;
                    return true;
                }
            }
            // 点击外部关闭下拉
            expanded = false;
            return true;
        } else if (isMouseOver(mouseX, mouseY)) {
            expanded = true;
            return true;
        }
        return false;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!this.visible) return false;

        boolean inMainArea = mouseX >= (double)this.getX() &&
                mouseY >= (double)this.getY() &&
                mouseX < (double)(this.getX() + this.width) &&
                mouseY < (double)(this.getY() + this.height);

        if (expanded) {
            boolean inDropdownArea = mouseX >= (double)this.getX() &&
                    mouseY >= (double)this.getY() + this.height &&
                    mouseX < (double)(this.getX() + this.width) &&
                    mouseY < (double)(this.getY() + this.height + options.size() * (height + 2));
            return inMainArea || inDropdownArea;
        }
        return inMainArea;
    }

    public static record Option(Text text, NbtElement value) {}
}