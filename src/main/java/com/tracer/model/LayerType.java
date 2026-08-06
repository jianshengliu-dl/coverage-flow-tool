package com.tracer.model;

/**
 * Represents the architectural layer of a Java class.
 * Used to build layered flow diagrams:
 *   CONTROLLER → SERVICE → REPOSITORY / COMPONENT / UTIL
 */
public enum LayerType {
    CONTROLLER("🌐 Controller", "#1565C0", "#E3F2FD"),
    SERVICE    ("⚙️  Service",    "#2E7D32", "#E8F5E9"),
    REPOSITORY ("🗄️  Repository", "#6A1B9A", "#F3E5F5"),
    COMPONENT  ("🔧 Component",  "#E65100", "#FFF3E0"),
    ENTITY     ("📦 Entity",     "#00695C", "#E0F2F1"),
    UTIL       ("🔩 Util",       "#4E342E", "#EFEBE9"),
    UNKNOWN    ("❓ Other",      "#546E7A", "#ECEFF1");

    public final String label;
    public final String borderColor;
    public final String bgColor;

    LayerType(String label, String borderColor, String bgColor) {
        this.label       = label;
        this.borderColor = borderColor;
        this.bgColor     = bgColor;
    }
}
