package com.auction.ui.util;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

/** App-styled confirmation dialogs shared by seller and admin auction actions. */
public final class AuctionUiDialogs {

  private static final int OPEN_MILLIS = 240;
  private static final int CLOSE_MILLIS = 160;
  private static StackPane activeOverlay;

  private AuctionUiDialogs() {}

  public static void showConfirmDialog(
      String title, String header, String body, boolean danger, Runnable onConfirm) {
    showConfirmDialog(
        title,
        header,
        body,
        danger,
        danger ? "Delete permanently" : "Yes, cancel auction",
        onConfirm);
  }

  public static void showConfirmDialog(
      String title,
      String header,
      String body,
      boolean danger,
      String confirmLabel,
      Runnable onConfirm) {
    if (activeOverlay != null && activeOverlay.getParent() != null) {
      return;
    }

    SceneManager sceneManager = SceneManager.getInstance();
    StackPane overlay = new StackPane();
    activeOverlay = overlay;
    overlay.getStyleClass().addAll("dialog-overlay", "auction-dialog-overlay");
    overlay.setAccessibleText(title);
    overlay.setFocusTraversable(true);

    VBox card = new VBox(0);
    card.getStyleClass().addAll("dialog-card", "auction-dialog-card");
    card.setMaxWidth(468);
    card.setMaxHeight(Region.USE_PREF_SIZE);
    card.setAlignment(Pos.TOP_CENTER);
    card.setOnMouseClicked(event -> event.consume());

    StackPane iconCircle = createIcon(danger);
    VBox iconWrap = new VBox(iconCircle);
    iconWrap.setAlignment(Pos.CENTER);
    iconWrap.setPadding(new Insets(28, 24, 8, 24));

    Label headerLabel = new Label(header);
    headerLabel.getStyleClass().add("dialog-title");
    configureCenteredLabel(headerLabel, 396);

    Label bodyLabel = new Label(body);
    bodyLabel.getStyleClass().add("dialog-body");
    configureCenteredLabel(bodyLabel, 388);

    VBox content = new VBox(10, headerLabel, bodyLabel);
    content.setAlignment(Pos.CENTER);
    content.setPadding(new Insets(8, 34, 24, 34));

    Button cancel = new Button("Cancel");
    cancel.getStyleClass().addAll("secondary-button", "auction-dialog-button");
    cancel.setMaxWidth(Double.MAX_VALUE);

    Button confirm = new Button(confirmLabel);
    confirm
        .getStyleClass()
        .addAll(
            "auction-dialog-button",
            danger ? "auction-dialog-button-danger" : "auction-dialog-button-warn");
    confirm.setMaxWidth(Double.MAX_VALUE);

    HBox buttons = new HBox(12, cancel, confirm);
    buttons.setAlignment(Pos.CENTER);
    buttons.setPadding(new Insets(0, 28, 28, 28));
    HBox.setHgrow(cancel, Priority.ALWAYS);
    HBox.setHgrow(confirm, Priority.ALWAYS);

    card.getChildren().addAll(iconWrap, content, buttons);
    overlay.getChildren().add(card);

    cancel.setOnAction(event -> close(sceneManager, overlay, card));
    confirm.setOnAction(
        event -> {
          close(sceneManager, overlay, card);
          onConfirm.run();
        });
    overlay.setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.ESCAPE) {
            close(sceneManager, overlay, card);
          } else if (event.getCode() == KeyCode.ENTER) {
            confirm.fire();
          }
        });

    overlay.setOpacity(0);
    card.setOpacity(0);
    card.setScaleX(0.965);
    card.setScaleY(0.965);
    card.setTranslateY(18);
    sceneManager.addModalOverlay(overlay);
    overlay.requestFocus();

    ParallelTransition open =
        new ParallelTransition(
            fade(overlay, 0, 1, 190),
            fade(card, 0, 1, OPEN_MILLIS),
            scale(card, 0.965, 1, OPEN_MILLIS),
            translate(card, 18, 0, OPEN_MILLIS));
    open.setInterpolator(Interpolator.EASE_OUT);
    open.setOnFinished(event -> confirm.requestFocus());
    open.play();
  }

  private static StackPane createIcon(boolean danger) {
    String iconSvg =
        danger
            ? "M19,4H15.5L14.5,3H9.5L8.5,4H5V6H19M6,19A2,2 0 0,0 8,21H16A2,2 "
                + "0 0,0 18,19V7H6V19Z"
            : "M13,14H11V10H13M13,18H11V16H13M1,21H23L12,2L1,21Z";

    StackPane circle = new StackPane();
    circle.getStyleClass().add(danger ? "dialog-icon-circle-danger" : "dialog-icon-circle-warn");
    circle.setMinSize(44, 44);
    circle.setPrefSize(44, 44);
    circle.setMaxSize(44, 44);

    Region icon = new Region();
    icon.getStyleClass().add("auction-dialog-icon-shape");
    icon.setStyle("-fx-shape: \"" + iconSvg + "\";");
    circle.getChildren().add(icon);
    return circle;
  }

  private static void configureCenteredLabel(Label label, double width) {
    label.setWrapText(true);
    label.setMaxWidth(width);
    label.setTextAlignment(TextAlignment.CENTER);
    label.setAlignment(Pos.CENTER);
  }

  private static void close(SceneManager sceneManager, StackPane overlay, VBox card) {
    if (overlay.getParent() == null) {
      return;
    }
    overlay.setDisable(true);
    ParallelTransition close =
        new ParallelTransition(
            fade(overlay, overlay.getOpacity(), 0, CLOSE_MILLIS),
            fade(card, card.getOpacity(), 0, CLOSE_MILLIS),
            scale(card, card.getScaleX(), 0.975, CLOSE_MILLIS),
            translate(card, card.getTranslateY(), 10, CLOSE_MILLIS));
    close.setInterpolator(Interpolator.EASE_IN);
    close.setOnFinished(
        event -> {
          sceneManager.removeOverlay(overlay);
          if (activeOverlay == overlay) {
            activeOverlay = null;
          }
        });
    close.play();
  }

  private static FadeTransition fade(Node node, double from, double to, int millis) {
    FadeTransition transition = new FadeTransition(Duration.millis(millis), node);
    transition.setFromValue(from);
    transition.setToValue(to);
    return transition;
  }

  private static ScaleTransition scale(Node node, double from, double to, int millis) {
    ScaleTransition transition = new ScaleTransition(Duration.millis(millis), node);
    transition.setFromX(from);
    transition.setFromY(from);
    transition.setToX(to);
    transition.setToY(to);
    return transition;
  }

  private static TranslateTransition translate(Node node, double from, double to, int millis) {
    TranslateTransition transition = new TranslateTransition(Duration.millis(millis), node);
    transition.setFromY(from);
    transition.setToY(to);
    return transition;
  }
}
