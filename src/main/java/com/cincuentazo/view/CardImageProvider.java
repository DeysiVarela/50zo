package com.cincuentazo.view;

import com.cincuentazo.model.Rank;
import com.cincuentazo.model.Suit;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.util.Objects;

/**
 * Loads the individual card PNG images from the cards folder.
 */
public final class CardImageProvider {

    private static final String CARDS_PATH = "/com/cincuentazo/cards/";
    private static final String BACK_IMAGE = "card_back.png";
    private static final String EMPTY_IMAGE = "card_empty.png";

    private CardImageProvider() {
    }

    public static StackPane cardBackPane(double width, double height) {
        StackPane pane = new StackPane();
        pane.getStyleClass().add("card-back-fallback");
        pane.setPrefSize(width, height);
        pane.setMinSize(width, height);
        pane.setMaxSize(width, height);
        pane.getChildren().add(cardBackView(width, height));
        return pane;
    }

    public static ImageView cardView(Rank rank, Suit suit, double width, double height) {
        return imageView(resourceFileName(rank, suit), width, height);
    }

    public static ImageView cardView(String shortString, double width, double height) {
        if (shortString == null || shortString.isBlank()) {
            return imageView(EMPTY_IMAGE, width, height);
        }

        int dash = shortString.indexOf('-');
        if (dash < 0) {
            return imageView(EMPTY_IMAGE, width, height);
        }

        String rankLabel = shortString.substring(0, dash);
        char suitChar = shortString.charAt(dash + 1);
        Rank rank = parseRank(rankLabel);
        Suit suit = parseSuit(suitChar);
        return cardView(rank, suit, width, height);
    }

    public static ImageView cardBackView(double width, double height) {
        return imageView(BACK_IMAGE, width, height);
    }

    public static void styleCardBack(StackPane target, double width, double height) {
        if (!target.getStyleClass().contains("card-back-fallback")) {
            target.getStyleClass().add("card-back-fallback");
        }
        target.setPrefSize(width, height);
        target.setMinSize(width, height);
        target.setMaxSize(width, height);
        target.getChildren().setAll(cardBackView(width, height));
    }

    private static ImageView imageView(String fileName, double width, double height) {
        ImageView view = new ImageView(loadImage(fileName));
        view.setFitWidth(width);
        view.setFitHeight(height);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    private static String resourceFileName(Rank rank, Suit suit) {
        String suitName = switch (suit) {
            case HEARTS -> "hearts";
            case DIAMONDS -> "diamonds";
            case CLUBS -> "clubs";
            case SPADES -> "spades";
        };
        String rankName = switch (rank) {
            case TWO -> "02";
            case THREE -> "03";
            case FOUR -> "04";
            case FIVE -> "05";
            case SIX -> "06";
            case SEVEN -> "07";
            case EIGHT -> "08";
            case NINE -> "09";
            case TEN -> "10";
            case JACK -> "J";
            case QUEEN -> "Q";
            case KING -> "K";
            case ACE -> "A";
        };
        return "card_" + suitName + "_" + rankName + ".png";
    }

    private static Image loadImage(String fileName) {
        return new Image(Objects.requireNonNull(
                CardImageProvider.class.getResource(CARDS_PATH + fileName),
                "No se encontró la imagen de carta: " + fileName
        ).toExternalForm(), false);
    }

    private static Rank parseRank(String label) {
        for (Rank rank : Rank.values()) {
            if (rank.getLabel().equals(label)) {
                return rank;
            }
        }
        return Rank.ACE;
    }

    private static Suit parseSuit(char code) {
        return switch (Character.toUpperCase(code)) {
            case 'H' -> Suit.HEARTS;
            case 'D' -> Suit.DIAMONDS;
            case 'C' -> Suit.CLUBS;
            case 'S' -> Suit.SPADES;
            default -> Suit.SPADES;
        };
    }
}
