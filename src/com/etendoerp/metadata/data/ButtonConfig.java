package com.etendoerp.metadata.data;

public class ButtonConfig {
  public final String id;
  public final String name;
  public final String action;
  public final boolean enabled;
  public final String icon;

  public ButtonConfig(String id, String name, String action, boolean enabled, String icon) {
    this.id = id;
    this.name = name;
    this.action = action;
    this.enabled = enabled;
    this.icon = icon;
  }
}