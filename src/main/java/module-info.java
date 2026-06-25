module com.cincuentazo {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.cincuentazo.controller to javafx.fxml;
}
