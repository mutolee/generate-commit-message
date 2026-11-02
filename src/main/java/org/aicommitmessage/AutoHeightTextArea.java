package org.aicommitmessage;

import com.intellij.ui.components.JBTextArea;

import javax.swing.SwingUtilities;
import java.awt.Dimension;

/** 按实际分配宽度换行并展开全部内容的文本区域，不以文本长度撑宽父容器。 */
final class AutoHeightTextArea extends JBTextArea {
    /** 创建不预留固定行数、按字符换行的文本区域。 */
    AutoHeightTextArea() {
        super(0, 0);
        setLineWrap(true); // 无空格的 URL 和 JSON 也必须在可用宽度内换行。
        setWrapStyleWord(false);
    }

    /** 仅提供换行后的首选高度，宽度完全由父布局分配。 */
    @Override
    public Dimension getPreferredSize() {
        Dimension preferred = super.getPreferredSize(); // Swing 根据当前组件宽度计算完整文本高度。
        return new Dimension(0, preferred.height);
    }

    /** 宽度变化后重新计算父布局高度，让窗口缩放时所有文本仍完整可见。 */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean widthChanged = width != getWidth();
        super.setBounds(x, y, width, height); // 先更新宽度，确保下一次测量使用新的换行位置。
        if (widthChanged) {
            // 等本轮布局结束再失效父容器，避免重新测量请求被正在进行的布局吞掉。
            SwingUtilities.invokeLater(this::revalidate);
        }
    }
}
