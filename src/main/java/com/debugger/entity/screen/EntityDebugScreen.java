package com.debugger.entity.screen;

import com.debugger.entity.EntityDebugger;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.*;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public class EntityDebugScreen extends Screen {
    private DropdownWidget activeDropdown;
    // 特殊NBT标签集合
    private static final Set<String> SPECIAL_TAGS = Set.of(
            "Pos", "Rotation", "Motion",
            "Health", "Air", "Fire",
            "Invulnerable", "PortalCooldown",
            "CustomName", "NoGravity"
    );
    // 类型颜色配置 - 更新为更现代化的颜色
    private static final Map<Byte, Integer> TYPE_COLORS = Map.ofEntries(
            Map.entry(NbtElement.END_TYPE, 0xFF555555),
            Map.entry(NbtElement.BYTE_TYPE, 0xFF4287f5),
            Map.entry(NbtElement.SHORT_TYPE, 0xFF4CAF50),
            Map.entry(NbtElement.INT_TYPE, 0xFFFFC107),
            Map.entry(NbtElement.LONG_TYPE, 0xFF9C27B0),
            Map.entry(NbtElement.FLOAT_TYPE, 0xFFE91E63),
            Map.entry(NbtElement.DOUBLE_TYPE, 0xFFF44336),
            Map.entry(NbtElement.BYTE_ARRAY_TYPE, 0xFF2196F3),
            Map.entry(NbtElement.STRING_TYPE, 0xFF8BC34A),
            Map.entry(NbtElement.LIST_TYPE, 0xFF00BCD4),
            Map.entry(NbtElement.COMPOUND_TYPE, 0xFF795548),
            Map.entry(NbtElement.INT_ARRAY_TYPE, 0xFFFF9800),
            Map.entry(NbtElement.LONG_ARRAY_TYPE, 0xFF673AB7)
    );

    // UI组件
    private TextFieldWidget activeEditor;
    private ButtonWidget prevButton, nextButton;

    // 数据状态
    private String editingKey;
    private final Map<String, NbtElement> nbtMap = new LinkedHashMap<>();
    private final List<String> nbtKeys = new ArrayList<>();
    private int currentPage = 1;
    private int itemsPerPage;
    private final Entity entity;

    // 类型选择相关
    private byte selectedType = NbtElement.STRING_TYPE;
    private final List<String> visibleKeys = new ArrayList<>();
    private TextFieldWidget addKeyField;
    private TextFieldWidget addValueField;

    // 修改状态
    private final Map<String, NbtElement> modifiedValues = new HashMap<>();
    private boolean hasUnsavedChanges = false;

    // 图标资源

    // 颜色常量
    private static final int PANEL_COLOR = 0xFF2D2D2D;
    private static final int HIGHLIGHT_COLOR = 0xFF3A3A3A;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int SECONDARY_TEXT_COLOR = 0xFFAAAAAA;
    private static final int ACCENT_COLOR = 0xFF4CAF50;
    private static final int WARNING_COLOR = 0xFFFFA000;
    // 在类顶部添加这些常量
    private static final int MARGIN = 10;
    private static final int PANEL_PADDING = 5;
    private static final int ENTRY_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 45;

    public EntityDebugScreen(Entity entity) {
        super(Text.literal("实体NBT编辑器"));
        this.entity = entity;
    }

    @Override
    protected void init() {
        super.init();
        // 计算每页显示的项目数
        calculatePageSize();

        // 初始化分页控件
        createPaginationControls();

        // 添加NBT按钮 - 使用更现代的风格
        this.addDrawableChild(ButtonWidget.builder(Text.literal("✚ 添加NBT"), button -> openAddNbtDialog())
                .dimensions(width - 110, 15, 90, 24)
                .tooltip(Tooltip.of(Text.literal("添加新的NBT标签")))
                .build());

        // 保存所有按钮 - 使用强调色
        this.addDrawableChild(ButtonWidget.builder(Text.literal("✔ 保存所有"), button -> {
                    if (activeEditor != null) {
                        saveEditedValue();
                    }
                    saveAllChanges();
                })
                .dimensions(width - 110, 45, 90, 24)
                .tooltip(Tooltip.of(Text.literal("保存所有修改")))
                .build());

        // 加载数据
        reloadNbtData();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 渲染自定义背景
        renderCustomBackground(context);

        // 渲染标题面板
        renderHeaderPanel(context);

        // 渲染NBT条目
        renderNbtEntries(context);

        // 渲染分页信息
        renderPagination(context);

        // 如果有活动的编辑器，渲染它
        if (activeEditor != null) {
            activeEditor.render(context, mouseX, mouseY, delta);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderCustomBackground(DrawContext context) {
        // 半透明暗色背景
        context.fill(0, 0, width, height, 0xCC000000);

        // 主内容区域背景
        int panelX = 10;
        int panelY = 10;
        int panelWidth = width - 20;
        int panelHeight = height - 20;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_COLOR);

        // 边框
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 1, ACCENT_COLOR); // 上边框
        context.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, ACCENT_COLOR); // 下边框
        context.fill(panelX, panelY, panelX + 1, panelY + panelHeight, ACCENT_COLOR); // 左边框
        context.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, ACCENT_COLOR); // 右边框
    }

    private void renderHeaderPanel(DrawContext context) {
        // 标题背景
        context.fill(15, 15, width - 15, 45, HIGHLIGHT_COLOR);

        // 标题文本
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("实体NBT编辑器").formatted(Formatting.BOLD),
                width / 2, 25, TEXT_COLOR);

        // 实体信息
        context.drawTextWithShadow(textRenderer,
                Text.literal(entity.getName().getString() + " - " + entity.getUuidAsString())
                        .formatted(Formatting.ITALIC),
                25, 50, SECONDARY_TEXT_COLOR);

        // 如果有未保存的修改，显示提示
        if (hasUnsavedChanges) {
            context.drawTextWithShadow(textRenderer,
                    Text.literal("有未保存的修改").formatted(Formatting.YELLOW),
                    width - 120, 75, WARNING_COLOR);
        }
    }

    private void renderNbtEntries(DrawContext context) {
        int startY = HEADER_HEIGHT + 15; // 从标题下方开始

        int startIdx = (currentPage - 1) * itemsPerPage;
        int endIdx = Math.min(startIdx + itemsPerPage, nbtKeys.size());

        // 渲染条目背景
        for (int i = 0; i < endIdx - startIdx; i++) {
            int y = startY + i * ENTRY_HEIGHT;
            int color = i % 2 == 0 ? PANEL_COLOR : HIGHLIGHT_COLOR;
            context.fill(MARGIN + PANEL_PADDING, y,
                    width - MARGIN - PANEL_PADDING,
                    y + ENTRY_HEIGHT - 2, color);
        }

        // 渲染条目内容
        for (int i = startIdx; i < endIdx; i++) {
            String key = nbtKeys.get(i);
            NbtElement value = nbtMap.get(key);
            int yPos = startY + (i - startIdx) * ENTRY_HEIGHT + 6;

            // 渲染键名
            context.drawText(textRenderer,
                    Text.literal(key).formatted(Formatting.YELLOW),
                    MARGIN + PANEL_PADDING + 5, yPos, TEXT_COLOR, false);

            // 渲染等号
            int equalsX = MARGIN + PANEL_PADDING + 5 + textRenderer.getWidth(key) + 5;
            context.drawText(textRenderer, "=",
                    equalsX, yPos, SECONDARY_TEXT_COLOR, false);

            // 渲染值（带类型颜色）
            int valueX = equalsX + 10;
            int color = TYPE_COLORS.getOrDefault(value.getType(), TEXT_COLOR);
            context.drawText(textRenderer, formatNbtValue(value),
                    valueX, yPos, color, false);
        }
    }

    private void renderPagination(DrawContext context) {
        // 分页控件背景
        context.fill(20, height - 40, width - 20, height - 15, HIGHLIGHT_COLOR);

        // 分页文本
        String pageInfo = String.format("第 %d/%d 页", currentPage, getTotalPages());
        context.drawCenteredTextWithShadow(textRenderer, pageInfo,
                width / 2, height - 30, TEXT_COLOR);
    }

    private void openAddNbtDialog() {
        Screen addNbtScreen = new Screen(Text.literal("添加NBT")) {
            // 尺寸常量
            private static final int DIALOG_WIDTH = 320;
            private static final int DIALOG_HEIGHT = 220; // 增加高度避免挤压
            private static final int PADDING = 20;

            // 坐标计算（动态获取）
            private int getDialogX() { return (width - DIALOG_WIDTH) / 2; }
            private int getDialogY() { return (height - DIALOG_HEIGHT) / 2; }

            // 垂直位置（从上到下计算）
            private int getTitleY() { return getDialogY() + 10; }
            private int getTypeButtonsY() { return getDialogY() + 40; }
            private int getKeyLabelY() { return getDialogY() + 75; } // 下移10px
            private int getKeyFieldY() { return getKeyLabelY() + 15; }
            private int getValueLabelY() { return getKeyFieldY() + 30; } // 增加间距
            private int getValueFieldY() { return getValueLabelY() + 15; }
            private int getButtonsY() { return getDialogY() + DIALOG_HEIGHT - 30; }

            @Override
            protected void init() {
                // 类型按钮（上移5px留出空间）
                addDrawableChild(ButtonWidget.builder(Text.literal("字节"), b -> updateType(NbtElement.BYTE_TYPE))
                        .dimensions(getDialogX() + PADDING, getTypeButtonsY() - 5, 80, 20).build());

                addDrawableChild(ButtonWidget.builder(Text.literal("整数"), b -> updateType(NbtElement.INT_TYPE))
                        .dimensions(getDialogX() + PADDING + 100, getTypeButtonsY() - 5, 80, 20).build());

                addDrawableChild(ButtonWidget.builder(Text.literal("小数"), b -> updateType(NbtElement.FLOAT_TYPE))
                        .dimensions(getDialogX() + PADDING + 200, getTypeButtonsY() - 5, 80, 20).build());

                // 键名输入框（下移10px）
                addKeyField = new TextFieldWidget(textRenderer,
                        getDialogX() + PADDING,
                        getKeyFieldY(),
                        DIALOG_WIDTH - 2*PADDING,
                        20,
                        Text.empty()
                );
                addKeyField.setPlaceholder(Text.literal("输入键名"));
                addDrawableChild(addKeyField);

                // 值输入框
                addValueField = new TextFieldWidget(textRenderer,
                        getDialogX() + PADDING,
                        getValueFieldY(),
                        DIALOG_WIDTH - 2*PADDING,
                        20,
                        Text.empty()
                );
                updateValueHint();
                addDrawableChild(addValueField);

                // 操作按钮
                addDrawableChild(ButtonWidget.builder(Text.literal("取消"), b -> close())
                        .dimensions(getDialogX() + PADDING, getButtonsY(), 120, 24).build());

                addDrawableChild(ButtonWidget.builder(Text.literal("确认"), b -> trySubmit())
                        .dimensions(getDialogX() + DIALOG_WIDTH - PADDING - 120, getButtonsY(), 120, 24).build());
            }

            @Override
            public void render(DrawContext context, int mouseX, int mouseY, float delta) {
                // 背景
                renderBackground(context);
                context.fill(0, 0, width, height, 0x80000000);

                // 对话框
                int x = getDialogX();
                int y = getDialogY();
                context.fill(x, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, 0xFF222222);
                context.fill(x, y, x + DIALOG_WIDTH, y + 30, 0xFF333333);

                // 标题
                context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("添加新NBT (" + getTypeName(selectedType) + ")"),
                        x + DIALOG_WIDTH/2, getTitleY(), 0xFFFFFF);

                // 标签（精确调整位置）
                context.drawText(textRenderer, "键名:",
                        x + PADDING,
                        getKeyLabelY(),
                        0xAAAAAA, false);

                context.drawText(textRenderer, "值:",
                        x + PADDING,
                        getValueLabelY(),
                        0xAAAAAA, false);

                super.render(context, mouseX, mouseY, delta);
            }

            private void trySubmit() {
                if (validateInput()) {
                    addNewNbtEntry(addKeyField.getText(), addValueField.getText());
                    close();
                }
            }

            private boolean validateInput() {
                if (addKeyField.getText().isEmpty()) {
                    addKeyField.setMessage(Text.literal("键名不能为空!").formatted(Formatting.RED));
                    return false;
                }

                try {
                    switch (selectedType) {
                        case NbtElement.BYTE_TYPE -> {
                            if (addValueField.getText().equalsIgnoreCase("true") ||
                                    addValueField.getText().equalsIgnoreCase("false")) return true;
                            Byte.parseByte(addValueField.getText());
                        }
                        case NbtElement.INT_TYPE -> Integer.parseInt(addValueField.getText());
                        case NbtElement.FLOAT_TYPE -> Float.parseFloat(addValueField.getText());
                    }
                } catch (NumberFormatException e) {
                    addValueField.setMessage(Text.literal("无效的" + getTypeName(selectedType) + "值!")
                            .formatted(Formatting.RED));
                    return false;
                }
                return true;
            }

            private void updateType(byte type) {
                selectedType = type;
                updateValueHint();
            }

            private void updateValueHint() {
                String hint = switch(selectedType) {
                    case NbtElement.BYTE_TYPE -> "0-255 或 true/false";
                    case NbtElement.INT_TYPE -> "整数 (如 123)";
                    case NbtElement.FLOAT_TYPE -> "小数 (如 3.14)";
                    default -> "输入值";
                };
                addValueField.setPlaceholder(Text.literal(hint));
            }

            private String getTypeName(byte type) {
                return switch(type) {
                    case NbtElement.BYTE_TYPE -> "字节";
                    case NbtElement.INT_TYPE -> "整数";
                    case NbtElement.FLOAT_TYPE -> "小数";
                    default -> "未知";
                };
            }

            // ... (其他方法保持不变，包括trySubmit/validateInput等)
        };

        // 重要：必须在设置屏幕前获取client实例
        if (this.client != null) {
            this.client.setScreen(addNbtScreen);
        }
    }
    private void openListEditor(NbtList list, String key) {
        NbtList workingCopy = list.copy();
        client.setScreen(new Screen(Text.literal("编辑列表: "+key)) {
            private NbtListEditorWidget editor;
            private ButtonWidget formatToggle;
            private boolean prettyFormat = true;

            @Override
            protected void init() {
                super.init();

                // 添加格式化切换按钮
                this.formatToggle = ButtonWidget.builder(
                        Text.literal(prettyFormat ? "原始格式" : "美化格式"),
                        button -> {
                            prettyFormat = !prettyFormat;
                            formatToggle.setMessage(Text.literal(prettyFormat ? "原始格式" : "美化格式"));
                            updateEditorContent();
                        }
                ).dimensions(width - 120, 10, 100, 20).build();
                addDrawableChild(formatToggle);

                // 创建增强版多行文本编辑器
                this.editor = new NbtListEditorWidget(
                        textRenderer,
                        20, 40,
                        width - 40, height - 100,
                        Text.literal("编辑NBT列表内容")
                );
                editor.setMaxLength(Integer.MAX_VALUE);
                updateEditorContent();
                addDrawableChild(editor);

                // 取消按钮
                addDrawableChild(ButtonWidget.builder(Text.literal("取消"), button -> returnToMainScreen()).dimensions(width/2 - 105, height - 50, 100, 20).build());

                // 保存按钮
                addDrawableChild(ButtonWidget.builder(Text.literal("保存"), button -> {
                    try {
                        NbtElement parsed = parseInputValue(editor.getText(), NbtElement.LIST_TYPE);
                        if (parsed instanceof NbtList) {
                            workingCopy.clear();
                            workingCopy.addAll((NbtList)parsed);
                            returnToMainScreen();
                        }
                    } catch (Exception e) {
                        showError("解析错误: " + e.getMessage());
                    }
                }).dimensions(width/2 + 5, height - 50, 100, 20).build());
            }

            @Override
            public void render(DrawContext context, int mouseX, int mouseY, float delta) {
                // 半透明背景
                context.fill(0, 0, width, height, 0x80000000);

                // 主编辑器背景
                context.fill(10, 10, width - 10, height - 10, PANEL_COLOR);

                // 标题栏
                context.fill(10, 10, width - 10, 40, HIGHLIGHT_COLOR);
                context.drawCenteredTextWithShadow(textRenderer,
                        "编辑列表: " + key + " (" + list.size() + "个元素)",
                        width / 2, 20, TEXT_COLOR);

                // 编辑器背景
                context.fill(20, 40, width - 20, height - 60, 0xFF1E1E1E);

                // 帮助文本
                context.drawText(textRenderer,
                        "使用标准NBT格式 | 上下箭头换行 | 括号自动高亮",
                        20, height - 30, SECONDARY_TEXT_COLOR, false);

                super.render(context, mouseX, mouseY, delta);
            }

            private void updateEditorContent() {
                if (prettyFormat) {
                    editor.setText(formatNbtListPretty(workingCopy));
                } else {
                    editor.setText(workingCopy.asString());
                }
            }

            private void returnToMainScreen() {
                // 检查是否有修改
                if (!workingCopy.equals(list)) {
                    modifiedValues.put(key, workingCopy.copy());
                    hasUnsavedChanges = true;
                    showSuccess("列表修改已暂存");
                }
                // 返回到主编辑界面
                client.setScreen(EntityDebugScreen.this);
            }

            @Override
            public void close() {
                returnToMainScreen();
            }

            private String formatNbtListPretty(NbtList list) {
                StringBuilder sb = new StringBuilder("[\n");
                for (int i = 0; i < list.size(); i++) {
                    sb.append("  ").append(formatNbtElementPretty(list.get(i), 1));
                    if (i < list.size() - 1) sb.append(",");
                    sb.append("\n");
                }
                return sb.append("]").toString();
            }

            private String formatNbtElementPretty(NbtElement element, int indent) {
                String indentStr = "  ".repeat(indent);
                if (element instanceof NbtCompound compound) {
                    StringBuilder sb = new StringBuilder("{\n");
                    int i = 0;
                    for (String key : compound.getKeys()) {
                        sb.append(indentStr).append("  ").append(key).append(": ")
                                .append(formatNbtElementPretty(compound.get(key), indent + 1));
                        if (++i < compound.getSize()) sb.append(",");
                        sb.append("\n");
                    }
                    return sb.append(indentStr).append("}").toString();
                } else if (element instanceof NbtList list) {
                    StringBuilder sb = new StringBuilder("[\n");
                    for (int i = 0; i < list.size(); i++) {
                        sb.append(indentStr).append("  ")
                                .append(formatNbtElementPretty(list.get(i), indent + 1));
                        if (i < list.size() - 1) sb.append(",");
                        sb.append("\n");
                    }
                    return sb.append(indentStr).append("]").toString();
                } else {
                    return element.asString();
                }
            }
        });
    }

    private void addNewNbtEntry(String key, String value) {
        if (key.isEmpty()) {
            showError("键名不能为空");
            return;
        }

        if (nbtMap.containsKey(key)) {
            showError("键名已存在: " + key);
            return;
        }

        try {
            NbtElement newValue = createNbtElement(value, selectedType);

            // 更新本地缓存
            nbtMap.put(key, newValue);
            nbtKeys.add(key);

            // 标记为已修改
            modifiedValues.put(key, newValue);  // 添加到修改列表
            hasUnsavedChanges = true;          // 标记有未保存修改

            // 更新UI但不立即保存到实体
            updateVisibleItems();

            showSuccess("添加成功: " + key);
        } catch (InvalidNbtFormatException e) {
            showError(e.getMessage());
        }
    }

    private NbtElement createNbtElement(String value, byte type) throws InvalidNbtFormatException {
        value = value.trim();
        try {
            switch (type) {
                case NbtElement.BYTE_TYPE:
                    if (value.equalsIgnoreCase("true")) return NbtByte.ONE;
                    if (value.equalsIgnoreCase("false")) return NbtByte.ZERO;
                    return NbtByte.of(Byte.parseByte(value));

                case NbtElement.SHORT_TYPE:
                    if (value.endsWith("s") || value.endsWith("S")) {
                        value = value.substring(0, value.length() - 1);
                    }
                    return NbtShort.of(Short.parseShort(value));

                case NbtElement.INT_TYPE:
                    return NbtInt.of(Integer.parseInt(value));

                case NbtElement.LONG_TYPE:
                    if (value.endsWith("L") || value.endsWith("l")) {
                        value = value.substring(0, value.length() - 1);
                    }
                    return NbtLong.of(Long.parseLong(value));

                case NbtElement.FLOAT_TYPE:
                    if (value.endsWith("F") || value.endsWith("f")) {
                        value = value.substring(0, value.length() - 1);
                    }
                    return NbtFloat.of(Float.parseFloat(value));

                case NbtElement.DOUBLE_TYPE:
                    return NbtDouble.of(Double.parseDouble(value));

                case NbtElement.STRING_TYPE:
                    return NbtString.of(value);

                case NbtElement.BYTE_ARRAY_TYPE:
                    if (!value.startsWith("[B;")) value = "[B;" + value;
                    if (!value.endsWith("]")) value = value + "]";
                    return StringNbtReader.parse(value);

                case NbtElement.INT_ARRAY_TYPE:
                    if (!value.startsWith("[I;")) value = "[I;" + value;
                    if (!value.endsWith("]")) value = value + "]";
                    return StringNbtReader.parse(value);

                case NbtElement.LONG_ARRAY_TYPE:
                    if (!value.startsWith("[L;")) value = "[L;" + value;
                    if (!value.endsWith("]")) value = value + "]";
                    return StringNbtReader.parse(value);

                case NbtElement.LIST_TYPE:
                    value = value.trim();
                    if (!value.startsWith("[")) value = "[" + value;
                    if (!value.endsWith("]")) value = value + "]";

                    // 处理浮点数f后缀
                    value = value.replaceAll("([0-9\\.]+)[fF]([,}\\]])", "$1$2");

                    // 处理简单数组默认转为整数列表
                    if (!value.startsWith("[I;") && !value.startsWith("[B;") &&
                            !value.startsWith("[L;") && !value.startsWith("[{") &&
                            !value.startsWith("[\"")) {
                        value = "[I;" + value.substring(1);
                    }

                    return StringNbtReader.parse(value);

                case NbtElement.COMPOUND_TYPE:
                    if (!value.startsWith("{")) value = "{" + value;
                    if (!value.endsWith("}")) value = value + "}";
                    return StringNbtReader.parse(value);

                default:
                    throw new InvalidNbtFormatException("不支持的NBT类型: " + getTypeName(type));
            }
        } catch (Exception e) {
            throw new InvalidNbtFormatException(getTypeName(type) + "类型解析失败: " + e.getMessage());
        }
    }

    private NbtElement parseNbtList(String input) throws Exception {
        input = input.trim();

        // 自动补全方括号
        if (!input.startsWith("[")) input = "[" + input;
        if (!input.endsWith("]")) input = input + "]";

        // 处理空列表
        if (input.equals("[]")) return new NbtList();

        // 如果是标准NBT格式（如[I;1,2,3]），直接解析
        if (input.startsWith("[I;") || input.startsWith("[B;") ||
                input.startsWith("[L;") || input.startsWith("[D;") ||
                input.startsWith("[F;") || input.startsWith("[{") ||
                input.startsWith("[\"")) {
            return StringNbtReader.parse(input);
        }

        // 处理嵌套结构和复杂元素
        return parseUniversalList(input);
    }

    private NbtElement parseUniversalList(String input) throws Exception {
        String content = input.substring(1, input.length() - 1).trim();
        if (content.isEmpty()) return new NbtList();

        NbtList result = new NbtList();
        int position = 0;
        int braceLevel = 0;  // 大括号层级
        int bracketLevel = 0; // 方括号层级
        StringBuilder currentElement = new StringBuilder();

        while (position < content.length()) {
            char c = content.charAt(position);

            // 处理嵌套结构
            if (c == '{') braceLevel++;
            if (c == '}') braceLevel--;
            if (c == '[') bracketLevel++;
            if (c == ']') bracketLevel--;

            // 遇到逗号且不在嵌套结构中，分割元素
            if (c == ',' && braceLevel == 0 && bracketLevel == 0) {
                addUniversalElement(result, currentElement.toString().trim());
                currentElement = new StringBuilder();
            } else {
                currentElement.append(c);
            }

            position++;
        }

        // 添加最后一个元素
        if (currentElement.length() > 0) {
            addUniversalElement(result, currentElement.toString().trim());
        }

        return result;
    }

    private void addUniversalElement(NbtList list, String element) throws Exception {
        if (element.isEmpty()) return;

        try {
            // 处理嵌套列表
            if (element.startsWith("[")) {
                list.add(parseNbtList(element));
                return;
            }

            // 处理复合标签
            if (element.startsWith("{")) {
                list.add(StringNbtReader.parse(element));
                return;
            }

            // 处理所有基本类型
            if (element.equalsIgnoreCase("true")) {
                list.add(NbtByte.ONE);
            } else if (element.equalsIgnoreCase("false")) {
                list.add(NbtByte.ZERO);
            }
            // 处理字节(带b后缀)
            else if (element.matches("^-?\\d+[bB]$")) {
                list.add(NbtByte.of(Byte.parseByte(element.substring(0, element.length()-1))));
            }
            // 处理短整型(带s后缀)
            else if (element.matches("^-?\\d+[sS]$")) {
                list.add(NbtShort.of(Short.parseShort(element.substring(0, element.length()-1))));
            }
            // 处理长整型(带l后缀)
            else if (element.matches("^-?\\d+[lL]$")) {
                list.add(NbtLong.of(Long.parseLong(element.substring(0, element.length()-1))));
            }
            // 处理double类型(带d后缀或科学计数法)
            else if (element.matches("^-?\\d+\\.\\d+([dD]|([eE][-+]?\\d+))?$")) {
                list.add(NbtDouble.of(Double.parseDouble(element.replaceAll("[dD]$", ""))));
            }
            // 处理float类型(带f后缀)
            else if (element.matches("^-?\\d+\\.?\\d*[fF]$")) {
                list.add(NbtFloat.of(Float.parseFloat(element.substring(0, element.length()-1))));
            }
            // 处理普通整数
            else if (element.matches("^-?\\d+$")) {
                list.add(NbtInt.of(Integer.parseInt(element)));
            }
            // 处理普通浮点数(无后缀)
            else if (element.matches("^-?\\d+\\.\\d+$")) {
                list.add(NbtDouble.of(Double.parseDouble(element)));
            }
            // 处理字符串
            else if (element.startsWith("\"") && element.endsWith("\"")) {
                list.add(NbtString.of(element.substring(1, element.length()-1)));
            } else {
                throw new Exception("无法识别的列表元素: " + element);
            }
        } catch (NumberFormatException e) {
            throw new Exception("数字格式错误: " + element);
        }
    }


    private void createPaginationControls() {
        // 统一按钮样式
        int buttonWidth = 30;  // 加宽按钮
        int buttonHeight = 20;
        int spacing = 10;     // 按钮间距

        // 底部居中布局
        int totalWidth = buttonWidth * 2 + spacing + textRenderer.getWidth("第1/1页");
        int startX = (width - totalWidth) / 2;
        int buttonY = height - 37;

        // 前一页按钮
        this.prevButton = ButtonWidget.builder(Text.literal("←"), btn -> {
                    cleanupEditing();
                    currentPage = Math.max(1, currentPage - 1);
                    updateVisibleItems();
                })
                .dimensions(startX, buttonY, buttonWidth, buttonHeight)
                .tooltip(Tooltip.of(Text.literal("上一页")))
                .build();

        // 页码文本位置
        int textX = startX + buttonWidth + spacing/2;

        // 后一页按钮
        this.nextButton = ButtonWidget.builder(Text.literal("→"), btn -> {
                    cleanupEditing();
                    currentPage = Math.min(getTotalPages(), currentPage + 1);
                    updateVisibleItems();
                })
                .dimensions(textX + textRenderer.getWidth("第1/1页") + spacing/2,
                        buttonY, buttonWidth, buttonHeight)
                .tooltip(Tooltip.of(Text.literal("下一页")))
                .build();

        addDrawableChild(prevButton);
        addDrawableChild(nextButton);
    }

    private void updateButtonStates() {
        // 添加空指针检查
        if (prevButton != null && nextButton != null) {
            prevButton.active = currentPage > 1;
            nextButton.active = currentPage < getTotalPages();
        }
    }

    private void updateVisibleItems() {
        // 重新排序键列表
        nbtKeys.sort(String::compareToIgnoreCase);

        // 更新分页
        int start = (currentPage - 1) * itemsPerPage;
        int end = Math.min(start + itemsPerPage, nbtKeys.size());

        visibleKeys.clear();
        for (int i = start; i < end; i++) {
            visibleKeys.add(nbtKeys.get(i));
        }
        updateButtonStates();
    }

    // === 核心功能方法 ===

    /**
     * 重新从实体加载NBT数据
     */
    private void reloadNbtData() {
        NbtCompound nbt = entity.writeNbt(new NbtCompound());
        nbtMap.clear();
        nbtKeys.clear();

        // 保持原始顺序但排序关键字段
        nbt.getKeys().stream()
                .sorted(Comparator.comparing(key -> {
                    if (key.equals("id")) return 0;
                    if (key.equals("Pos")) return 1;
                    if (key.equals("Rotation")) return 2;
                    return 3;
                }))
                .forEach(key -> {
                    nbtMap.put(key, nbt.get(key));
                    nbtKeys.add(key);
                });

        calculatePageSize();
        updateVisibleItems();
    }

    /**
     * 计算每页显示项数
     */
    private void calculatePageSize() {
        itemsPerPage = Math.max(1, (height - 80) / 20 - 1); // 保留底部空间
    }

    private Text formatNbtValue(NbtElement value) {
        Formatting color = Formatting.byColorIndex(TYPE_COLORS.getOrDefault(value.getType(), 0xFFFFFFFF));
        String text = switch (value.getType()) {
            case NbtElement.STRING_TYPE -> "\"" + value.asString() + "\"";
            case NbtElement.COMPOUND_TYPE -> "{...}";
            case NbtElement.LIST_TYPE -> "List[" + ((NbtList)value).size() + "]";
            case NbtElement.BYTE_ARRAY_TYPE -> "ByteArray[" + ((NbtByteArray)value).getByteArray().length + "]";
            case NbtElement.INT_ARRAY_TYPE -> "IntArray[" + ((NbtIntArray)value).getIntArray().length + "]";
            case NbtElement.LONG_ARRAY_TYPE -> "LongArray[" + ((NbtLongArray)value).getLongArray().length + "]";
            case NbtElement.BYTE_TYPE ->
                    value.equals(NbtByte.ONE) ? "true" :
                            value.equals(NbtByte.ZERO) ? "false" :
                                    value.asString();
            case NbtElement.SHORT_TYPE -> value.asString(); // 已经包含s后缀
            case NbtElement.FLOAT_TYPE -> value.asString();
            case NbtElement.LONG_TYPE -> value.asString();
            default -> value.asString();
        };
        return Text.literal(text).formatted(color);
    }

    // === 交互处理方法 ===

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // 先处理下拉框的点击（如果存在活动下拉框）
        if (activeDropdown != null && activeDropdown.parentMouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // 如果点击了保存按钮区域，不再处理编辑
        if (mouseX >= width - 100 && mouseX <= width - 20 &&
                mouseY >= 45 && mouseY <= 65) {
            return true;
        }

        // 如果点击了非编辑区域，清理所有编辑框和下拉框
        if (button == 0 && !isMouseOverEditor(mouseX, mouseY)) {
            clearActiveEditors();
            if (activeDropdown != null) {
                activeDropdown.setExpanded(false);
                activeDropdown = null;
            }
            return true; // 阻止进一步处理
        }

        if (button == 0) {
            int clickedIndex = getClickedIndex(mouseX, mouseY);
            if (clickedIndex >= 0) {
                startEditing(clickedIndex);
                return true;
            }
        }

        if (activeEditor != null && activeEditor.isMouseOver(mouseX, mouseY)) {
            // 允许编辑器内部处理点击（选择文本等）
            boolean handled = activeEditor.mouseClicked(mouseX, mouseY, button);

            // 只在点击编辑器外部时保存
            if (!handled && button == 0) {
                saveEditedValue();
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    // 添加辅助方法
    private boolean isMouseOverEditor(double mouseX, double mouseY) {
        // 检查是否在活动文本编辑器上方
        if (activeEditor != null && activeEditor.isMouseOver(mouseX, mouseY)) {
            return true;
        }
        // 检查是否在活动下拉框上方
        if (activeDropdown != null && activeDropdown.isMouseOver(mouseX, mouseY)) {
            return true;
        }
        // 检查是否在保存按钮区域
        if (mouseX >= width - 100 && mouseX <= width - 20 &&
                mouseY >= 45 && mouseY <= 65) {
            return true;
        }
        // 检查是否在其他UI组件上方（按钮等）
        for (var child : children()) {
            if (child instanceof ClickableWidget widget && widget.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    // 添加清理方法
    public void clearActiveEditors() {
        if (activeEditor != null) {
            remove(activeEditor);
            activeEditor = null;
        }
        editingKey = null;
        setFocused(null);
    }

    private int getClickedIndex(double mouseX, double mouseY) {
        int startY = 60; // 与renderNbtEntries一致
        int entryHeight = 20; // 与renderNbtEntries一致

        if (mouseX < 20 || mouseX > width - 20 || mouseY < startY || mouseY > startY + itemsPerPage * entryHeight) {
            return -1;
        }

        int relativeY = (int)(mouseY - startY);
        int index = relativeY / entryHeight;

        int startIdx = (currentPage - 1) * itemsPerPage;
        if (index >= 0 && (startIdx + index) < nbtKeys.size()) {
            return index;
        }
        return -1;
    }

    private void startEditing(int relativeIndex) {
        clearActiveEditors();

        int absoluteIndex = (currentPage - 1) * itemsPerPage + relativeIndex;
        if (absoluteIndex >= nbtKeys.size()) return;

        editingKey = nbtKeys.get(absoluteIndex);
        NbtElement value = nbtMap.get(editingKey);

        if (value instanceof NbtList) {
            openListEditor((NbtList) value, editingKey);
            return;
        }

        // 动态计算位置
        int startY = HEADER_HEIGHT + 15;
        int yPos = startY + relativeIndex * ENTRY_HEIGHT;

        // 计算键名宽度
        int keyWidth = textRenderer.getWidth(editingKey);

        // 计算编辑器位置和大小
        int editorX = MARGIN + PANEL_PADDING + 5 + keyWidth + 15;
        int editorWidth = width - editorX - MARGIN - PANEL_PADDING - 5;

        // 特殊处理布尔值 - 使用下拉框
        if (value.getType() == NbtElement.BYTE_TYPE &&
                (value.equals(NbtByte.ONE) || value.equals(NbtByte.ZERO))) {
            // 创建下拉框
            DropdownWidget dropdown = new DropdownWidget(
                    this, // 传入当前屏幕实例
                    editorX,
                    yPos,
                    editorWidth,
                    ENTRY_HEIGHT - 2,
                    Text.literal("选择布尔值"),
                    List.of(
                            new DropdownWidget.Option(Text.literal("true"), NbtByte.ONE),
                            new DropdownWidget.Option(Text.literal("false"), NbtByte.ZERO)
                    ),
                    value.equals(NbtByte.ONE) ? 0 : 1
            );

            dropdown.setChangedListener(selected -> {
                modifiedValues.put(editingKey, selected.value());
                hasUnsavedChanges = true;
                syncToEntity(editingKey, selected.value());
                activeDropdown = null; // 选择后清除下拉框引用
                clearActiveEditors(); // 清理编辑状态
                showSuccess("布尔值已更新: " + editingKey);
            });

            addDrawableChild(dropdown);
            setFocused(dropdown);
            activeDropdown = dropdown; // 设置当前活动下拉框
            return;
        }

        // 其他类型保持原样
        activeEditor = new TextFieldWidget(
                textRenderer,
                editorX,
                yPos,
                editorWidth,
                ENTRY_HEIGHT - 2,
                Text.literal("编辑: " + editingKey)
        );

        activeEditor.setText(value.asString());
        activeEditor.setChangedListener(this::validateInput);
        addSelectableChild(activeEditor);
        setFocused(activeEditor);
    }

    private void validateInput(String text) {
        if (activeEditor == null || editingKey == null) {
            return;
        }

        try {
            NbtElement original = nbtMap.get(editingKey);
            if (original == null) {
                return;
            }

            byte expectedType = original.getType();

            // 布尔值特殊验证
            if (expectedType == NbtElement.BYTE_TYPE) {
                if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
                    activeEditor.setEditableColor(0xFFFFFF);
                    return;
                }
            }

            parseInputValue(text, expectedType);
            activeEditor.setEditableColor(0xFFFFFF);
        } catch (Exception e) {
            if (activeEditor != null) {
                activeEditor.setEditableColor(0xFF5555);
            }
        }
    }

    private void saveAllChanges() {
        if (!hasUnsavedChanges || modifiedValues.isEmpty()) {
            showError("没有需要保存的修改");
            return;
        }

        try {
            NbtCompound entityNbt = entity.writeNbt(new NbtCompound());

            // 应用所有修改
            for (Map.Entry<String, NbtElement> entry : modifiedValues.entrySet()) {
                String key = entry.getKey();
                NbtElement value = entry.getValue().copy();

                // 更新NBT
                entityNbt.put(key, value);

                // 即时同步到实体属性
                syncToEntity(key, value);
            }

            // 读取回实体以确保所有更改生效
            entity.readNbt(entityNbt);

            showSuccess("成功保存 " + modifiedValues.size() + " 处修改");
            modifiedValues.clear();
            hasUnsavedChanges = false;
            reloadNbtData();
        } catch (Exception e) {
            showError("保存失败: " + e.getMessage());
        }
    }

    private void syncToEntity(String key, NbtElement value) {
        try {
            switch (key) {
                case "Pos":
                    if (value instanceof NbtList list && list.size() >= 3) {
                        double x = list.getDouble(0);
                        double y = list.getDouble(1);
                        double z = list.getDouble(2);
                        entity.updatePosition(x, y, z);
                        entity.setPos(x, y, z); // 确保位置完全更新
                    }
                    break;

                case "Rotation":
                    if (value instanceof NbtList list && list.size() >= 2) {
                        float yaw = list.getFloat(0);
                        float pitch = list.getFloat(1);
                        entity.setYaw(yaw);
                        entity.setPitch(pitch);
                        // 对于LivingEntity还需要更新头部旋转
                        if (entity instanceof LivingEntity living) {
                            living.setHeadYaw(yaw);
                            living.bodyYaw = yaw;
                        }
                    }
                    break;

                case "Health":
                    if (entity instanceof LivingEntity living) {
                        if (value instanceof NbtFloat health) {
                            living.setHealth(health.floatValue());
                        } else if (value instanceof NbtList list && !list.isEmpty()) {
                            living.setHealth(list.getFloat(0));
                        }
                    }
                    break;

                case "Motion":
                    if (value instanceof NbtList list && list.size() >= 3) {
                        double mx = list.getDouble(0);
                        double my = list.getDouble(1);
                        double mz = list.getDouble(2);
                        entity.setVelocity(mx, my, mz);
                    }
                    break;

                case "Air":
                    if (entity instanceof LivingEntity living) {
                        if (value instanceof NbtShort air) {
                            living.setAir(air.shortValue());
                        }
                    }
                    break;

                case "Fire":
                    entity.setFireTicks(value instanceof NbtShort fire ? fire.shortValue() : 0);
                    break;

                case "Invulnerable":
                    entity.setInvulnerable(value instanceof NbtByte invul && invul.byteValue() != 0);
                    break;

                case "PortalCooldown":
                    entity.setPortalCooldown(value instanceof NbtInt cooldown ? cooldown.intValue() : 0);
                    break;

                case "CustomName":
                    if (value instanceof NbtString name) {
                        entity.setCustomName(Text.literal(name.asString()));
                    }
                    break;

                case "NoGravity":
                    entity.setNoGravity(value instanceof NbtByte noGrav && noGrav.byteValue() != 0);
                    break;
            }
        } catch (Exception e) {
            EntityDebugger.LOGGER.error("同步{}到实体失败: {}", key, e.getMessage());
        }
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);

        // 重新计算每页项目数
        calculatePageSize();

        // 重新初始化UI组件
        this.init(client, width, height);

        // 如果有活动的编辑器，需要重新定位
        if (activeEditor != null) {
            int relativeIndex = nbtKeys.indexOf(editingKey) % itemsPerPage;
            cleanupEditing();
            startEditing(relativeIndex);
        }
    }

    private void saveEditedValue() {
        try {
            if (editingKey == null || activeEditor == null) return;

            String input = activeEditor.getText().trim();
            NbtElement original = nbtMap.get(editingKey);
            NbtElement newValue = parseInputValue(input, original.getType());

            // 更新本地缓存
            modifiedValues.put(editingKey, newValue);
            nbtMap.put(editingKey, newValue);
            hasUnsavedChanges = true;

            // 即时同步到实体
            syncToEntity(editingKey, newValue);

            showSuccess("保存成功: " + editingKey);
        } catch (Exception e) {
            showError("保存失败: " + e.getMessage());
        } finally {
            cleanupEditing();
        }
    }

    private NbtElement parseInputValue(String input, byte expectedType) throws Exception {
        input = input.trim();
        try {
            // 处理空输入
            if (input.isEmpty()) {
                throw new Exception("输入不能为空");
            }

            if (expectedType == NbtElement.LIST_TYPE) {
                return parseNbtList(input);
            }

            // 根据期望类型进行预处理
            switch (expectedType) {

                case NbtElement.COMPOUND_TYPE:
                    // 自动补全复合标签格式
                    if (!input.startsWith("{")) input = "{" + input;
                    if (!input.endsWith("}")) input = input + "}";
                    break;

                case NbtElement.BYTE_ARRAY_TYPE:
                    if (!input.startsWith("[B;")) input = "[B;" + input.substring(1);
                    break;

                case NbtElement.INT_ARRAY_TYPE:
                    if (!input.startsWith("[I;")) input = "[I;" + input.substring(1);
                    break;

                case NbtElement.LONG_ARRAY_TYPE:
                    if (!input.startsWith("[L;")) input = "[L;" + input.substring(1);
                    break;
            }

            // 特殊处理布尔值
            if (expectedType == NbtElement.BYTE_TYPE) {
                if (input.equalsIgnoreCase("true")) return NbtByte.ONE;
                if (input.equalsIgnoreCase("false")) return NbtByte.ZERO;
            }

            // 解析NBT
            NbtElement parsed;
            try {
                parsed = StringNbtReader.parse(input);
            } catch (CommandSyntaxException e) {
                // 提供更友好的错误信息
                String msg = e.getMessage();
                if (msg.contains("expected key")) {
                    throw new Exception("复合标签缺少键名或冒号，正确格式: key: value");
                } else if (msg.contains("at line") && msg.contains("column")) {
                    throw new Exception("语法错误(行" +
                            msg.substring(msg.indexOf("line") + 5, msg.indexOf(",")) +
                            " 列" + msg.substring(msg.indexOf("column") + 7, msg.indexOf(")")) + ")");
                } else if (msg.contains("Expected value")) {
                    throw new Exception("缺少值或格式不正确");
                } else if (msg.contains("Invalid escape sequence")) {
                    throw new Exception("无效的转义字符");
                }
                throw new Exception("NBT格式错误: " + msg);
            }

            // 验证类型
            if (parsed.getType() != expectedType) {
                String expected = getTypeName(expectedType);
                String actual = getTypeName(parsed.getType());

                // 特殊处理字节数组/整数数组的混淆情况
                if ((expectedType == NbtElement.BYTE_ARRAY_TYPE && parsed.getType() == NbtElement.INT_ARRAY_TYPE) ||
                        (expectedType == NbtElement.INT_ARRAY_TYPE && parsed.getType() == NbtElement.BYTE_ARRAY_TYPE)) {
                    throw new Exception("数组类型不匹配！需要 " + expected + " 但输入是 " + actual +
                            "\n提示: 字节数组使用 [B;...]，整数数组使用 [I;...]");
                }

                throw new Exception("类型不匹配！需要 " + expected + " 但输入是 " + actual);
            }

            if (editingKey != null && SPECIAL_TAGS.contains(editingKey)) {
                switch (editingKey) {
                    case "Pos":
                    case "Motion":
                        if (!input.matches("\\[\\s*-?\\d+\\.?\\d*\\s*,\\s*-?\\d+\\.?\\d*\\s*,\\s*-?\\d+\\.?\\d*\\s*\\]")) {
                            throw new Exception("需要3个坐标值，格式如: [x, y, z]");
                        }
                        break;

                    case "Rotation":
                        if (!input.matches("\\[\\s*-?\\d+\\.?\\d*\\s*,\\s*-?\\d+\\.?\\d*\\s*\\]")) {
                            throw new Exception("需要2个角度值，格式如: [yaw, pitch]");
                        }
                        break;

                    case "Health":
                        if (!input.matches("-?\\d+\\.?\\d*")) {
                            throw new Exception("需要生命值数字");
                        }
                        float health = Float.parseFloat(input);
                        if (health <= 0) {
                            throw new Exception("生命值必须大于0");
                        }
                        break;
                }
            }

            return parsed;
        } catch (NumberFormatException e) {
            throw new Exception("数字格式错误: " + e.getMessage());
        } catch (Exception e) {
            // 添加额外上下文信息
            throw new Exception("解析" + getTypeName(expectedType) + "时出错: " + e.getMessage());
        }
    }

    // 显示保存成功
    // 显示错误（红色）
    private void showError(String message) {
        // 聊天栏消息
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.sendMessage(Text.literal("§c[NBT错误] §f" + message), false);
        }

        EntityDebugger.LOGGER.error("NBT错误: {}", message);
    }

    // 显示成功（绿色）
    private void showSuccess(String message) {
        // 聊天栏消息
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.sendMessage(Text.literal("§a[NBT] §f" + message), false);
        }
    }

    // 在文件顶部添加自定义异常（可选）
    static class InvalidNbtFormatException extends RuntimeException {
        public InvalidNbtFormatException(String message) {
            super(message);
        }
    }

    private String getTypeName(byte nbtType) {
        return switch (nbtType) {
            case NbtElement.END_TYPE -> "结束标签";
            case NbtElement.BYTE_TYPE -> "字节/布尔";
            case NbtElement.SHORT_TYPE -> "短整型";
            case NbtElement.INT_TYPE -> "整数";
            case NbtElement.LONG_TYPE -> "长整型";
            case NbtElement.FLOAT_TYPE -> "浮点数";
            case NbtElement.DOUBLE_TYPE -> "双精度";
            case NbtElement.BYTE_ARRAY_TYPE -> "字节数组";
            case NbtElement.STRING_TYPE -> "字符串";
            case NbtElement.LIST_TYPE -> "列表";
            case NbtElement.COMPOUND_TYPE -> "复合标签";
            case NbtElement.INT_ARRAY_TYPE -> "整数数组";
            case NbtElement.LONG_ARRAY_TYPE -> "长整型数组";
            default -> "未知类型(" + nbtType + ")";
        };
    }


    private int getTotalPages() {
        return (int) Math.ceil((double) nbtKeys.size() / itemsPerPage);
    }

    private void cleanupEditing() {
        if (activeEditor != null) {
            remove(activeEditor); // 从屏幕移除
            activeEditor = null;
        }
        editingKey = null;
        setFocused(null); // 移除焦点
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeEditor != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                saveEditedValue();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cleanupEditing();
                return true;
            }
            return activeEditor.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (hasUnsavedChanges) {
            // 显示确认对话框
            this.client.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) {
                    saveAllChanges();
                    super.close();
                } else {
                    super.close();
                }
            }, Text.literal("未保存的修改"),
                    Text.literal("您有未保存的修改，是否要保存？")));
        } else {
            super.close();
        }
    }
}