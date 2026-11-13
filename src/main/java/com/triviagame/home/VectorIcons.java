package com.triviagame.home;
import javafx.scene.Node;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
/** Small resolution-independent vector icons; no icon font or dependency needed. */
public final class VectorIcons {
 private VectorIcons() { }
 public static Node of(String icon) {
 String path = switch (icon) {
 case "home" -> "M3 10.5 12 3l9 7.5 M5 10v11h14V10 M9 21v-7h6v7";
 case "users" -> "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8a4 4 0 0 0 0 8 M22 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75";
 case "play" -> "M8 5v14l11-7z";
 case "chart" -> "M3 3v18h18 M7 15l4-4 4 3 6-8";
 case "list" -> "M8 6h13 M8 12h13 M8 18h13 M3 6h.01 M3 12h.01 M3 18h.01";
 case "download" -> "M12 3v12 M7 10l5 5 5-5 M4 17v4h16v-4";
 case "refresh" -> "M20 11a8 8 0 0 0-14-5L3 9 M3 3v6h6 M4 13a8 8 0 0 0 14 5l3-3 M15 15h6v6";
 case "trash" -> "M3 6h18 M8 6V4h8v2 M6 6l1 15h10l1-15 M10 10v7 M14 10v7";
 case "check" -> "M4 12l5 5L20 6";
 case "exit" -> "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4 M16 17l5-5-5-5 M21 12H9";
 default -> "M12 3v18 M3 12h18";
 };
 SVGPath shape = new SVGPath();
 shape.setContent(path);
 shape.setFill(Color.TRANSPARENT);
 shape.setStroke(Color.web("#E8E5FF"));
 shape.setStrokeWidth(1.8);
 shape.setScaleX(.75);
 shape.setScaleY(.75);
 return new Group(shape);
 }
 public static Node heart(boolean full) {
 SVGPath shape = new SVGPath();
 shape.setContent("M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67"
 + "l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06"
 + "L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z");
 shape.setStroke(Color.web(full ? "#FB7185" : "#63657C"));
 shape.setStrokeWidth(1.7);
 shape.setFill(full ? Color.web("#FB7185") : Color.TRANSPARENT);
 shape.setScaleX(.8);
 shape.setScaleY(.8);
 return new Group(shape);
 }
}
