module com.company.monkeytypeproject {
    requires javafx.controls;


    opens com.company.monkeytypeproject to javafx.fxml;
    exports com.company.monkeytypeproject;
}