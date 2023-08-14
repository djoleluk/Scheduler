import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.*;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class Scheduler extends Application {
    private static final int WIDTH = 850;
    private static final int HEIGHT = 530;
    private final Preferences preferences = Preferences.userNodeForPackage(Scheduler.class); // use Preferences to save data
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors()); // executor service for application tasks
    private ScheduledFuture<?> sessionCounterAction; // reference to the counter task
    private LocalTime startTime, endTime;
    private Duration totalToday;
    private Duration totalThisWeek;
    private static final String SESSIONS_KEY = "number_of_sessions(" + LocalDate.now().toString() + ")";
    private static final String LAST_SESSION_KEY = "last_session(" + LocalDate.now().toString() + ")";
    private static final String LONGEST_SESSION_KEY = "longest_session(" + LocalDate.now().toString() + ")";
    private static final String TOTAL_TIME_TODAY_KEY = "total_today(" + LocalDate.now().toString() + ")";
    private static final String TOTAL_TIME_WEEK_KEY = "total_this_week";
    private static final String WEEKLY_RESET_KEY = "week_reset";
    private static final String SAVED_DATA_KEY = "saved_data";
    private CategoryAxis xAxis;
    private NumberAxis yAxis;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Scheduler");
        primaryStage.setScene(languageSelectionScene(primaryStage));
        primaryStage.show();

        // shutdown all tasks on exit
        primaryStage.setOnCloseRequest(event -> {
            if (!executorService.isShutdown()) {
                executorService.shutdownNow();
            }
        });
    }

    private Scene languageSelectionScene(Stage primaryStage) {
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/icon.png")));

        // language selection txt
        Label title = new Label("Select Language");
        title.setId("langTitle");

        // buttons for language selection
        Button en = new Button("English");
        en.setId("langButton");
        Button sr = new Button("Serbian");
        sr.setId("langButton");

        // set language to english
        en.setOnAction(event -> {
            ResourceBundle languageData = ResourceBundle.getBundle("language", new Locale("en"));
            primaryStage.setScene(mainScene(primaryStage, languageData));
        });

        // set language to serbian
        sr.setOnAction(event -> {
            ResourceBundle languageData = ResourceBundle.getBundle("language", new Locale("sr"));
            primaryStage.setScene(mainScene(primaryStage, languageData));
        });

        // hBox to place the title
        HBox titleBox = new HBox();
        titleBox.setAlignment(Pos.CENTER);
        titleBox.getChildren().add(title);

        // hBox to place the buttons
        HBox hBox = new HBox();
        hBox.setAlignment(Pos.CENTER);
        hBox.setSpacing(30);
        hBox.getChildren().addAll(en, sr);

        VBox vBox = new VBox();
        vBox.setAlignment(Pos.CENTER);
        vBox.setSpacing(50);
        vBox.getChildren().addAll(titleBox, hBox);

        BorderPane borderPane = new BorderPane();
        borderPane.setCenter(vBox);
        borderPane.setId("langSelectionPane");

        Scene scene = new Scene(borderPane, WIDTH, HEIGHT);
        scene.getStylesheets().add(getClass().getResource("styles").toExternalForm());
        return scene;
    }

    private Scene mainScene(Stage primaryStage, ResourceBundle languageData) {

        Label currentDate = new Label(languageData.getString("1") + preferences.get(LocalDate.now().toString(), "n/o"));
        currentDate.setId("dataLabel");

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                .withLocale(languageData.getLocale());
        if (languageData.getLocale().equals(new Locale("sr"))) {
            dateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM, yyyy");
        }
        currentDate.setText(languageData.getString("1") + LocalDate.now().format(dateTimeFormatter));

        // weekly reset on startup
        if (LocalDate.now().getDayOfWeek() == DayOfWeek.MONDAY && preferences.getBoolean(WEEKLY_RESET_KEY, true)) {
            preferences.putBoolean(WEEKLY_RESET_KEY, false);
            preferences.put(TOTAL_TIME_WEEK_KEY, "PT0S");
            totalToday = Duration.of(0, ChronoUnit.SECONDS);
            totalThisWeek = Duration.of(0, ChronoUnit.SECONDS);
            // daily study time reset
            Arrays.stream(DayOfWeek.values()).forEach(dayOfWeek -> preferences.put(dayOfWeek.toString(), "PT0S"));
        } else if (LocalDate.now().getDayOfWeek() != DayOfWeek.MONDAY) {
            preferences.putBoolean(WEEKLY_RESET_KEY, true);
        }

        Label numberOfSessions = new Label(languageData.getString("2") + preferences.get(SESSIONS_KEY, "0"));
        numberOfSessions.setId("dataLabel");

        Label lastSessionDuration = new Label(languageData.getString("3") + formatTime(preferences.get(LAST_SESSION_KEY, "PT0S")));
        lastSessionDuration.setId("dataLabel");

        Label longestSession = new Label(languageData.getString("4") + formatTime(preferences.get(LONGEST_SESSION_KEY, "PT0S")));
        longestSession.setId("dataLabel");

        Label totalTimeToday = new Label(languageData.getString("5") + formatTime(preferences.get(TOTAL_TIME_TODAY_KEY, "PT0S")));
        totalTimeToday.setId("dataLabel");

        Label totalTimeThisWeek = new Label(languageData.getString("6") + formatTime(preferences.get(TOTAL_TIME_WEEK_KEY, "PT0S")));
        totalTimeThisWeek.setId("dataLabel");

        // create array of main labels
        Node[] mainLabels = {currentDate, numberOfSessions, lastSessionDuration, longestSession,
                totalTimeToday, totalTimeThisWeek};

        Label sessionStarted = new Label(languageData.getString("14"));
        sessionStarted.setId("dataLabel");

        Label sessionFinished = new Label(languageData.getString("15"));
        sessionFinished.setId("dataLabel");

        Label elapsedSessionTime = new Label(languageData.getString("16"));
        elapsedSessionTime.setId("dataLabel");

        // create array of progress labels
        Node[] progressLabels = {sessionStarted, sessionFinished, elapsedSessionTime};

        // control buttons
        Button startSession = new Button(languageData.getString("7")); // button to start the session
        startSession.setId("regularButton");

        Button clearDailyData = new Button(languageData.getString("9")); // button to clear daily results
        clearDailyData.setId("regularButton");

        Button clearWeeklyData = new Button(languageData.getString("10")); // button to clear weekly results
        clearWeeklyData.setId("regularButton");

        // tool panel buttons
        Button mainData = new Button();
        mainData.setGraphic(new ImageView(new Image(getClass().getResourceAsStream("/data.png"))));
        mainData.setTooltip(new Tooltip(languageData.getString("11")));
        mainData.setId("sideButton");

        Button elapsedTime = new Button();
        elapsedTime.setGraphic(new ImageView(new Image(getClass().getResourceAsStream("/timer.png"))));
        elapsedTime.setTooltip(new Tooltip(languageData.getString("12")));
        elapsedTime.setId("sideButton");

        Button chartSelector = new Button();
        chartSelector.setGraphic(new ImageView(new Image(getClass().getResourceAsStream("/chart.png"))));
        chartSelector.setTooltip(new Tooltip(languageData.getString("13")));
        chartSelector.setId("sideButton");

        Button saveResults = new Button();
        saveResults.setGraphic(new ImageView(new Image(getClass().getResourceAsStream("/save.png"))));
        saveResults.setTooltip(new Tooltip(languageData.getString("17")));
        saveResults.setId("sideButton");

        Button loadResults = new Button();
        loadResults.setGraphic(new ImageView(new Image(getClass().getResourceAsStream("/load.png"))));
        loadResults.setTooltip(new Tooltip(languageData.getString("18")));
        loadResults.setId("sideButton");

        Button showNotes = new Button();
        showNotes.setGraphic(new ImageView(new Image(getClass().getResourceAsStream("/notes.png"))));
        showNotes.setTooltip(new Tooltip(languageData.getString("25")));
        showNotes.setId("sideButton");

        // set up a bar chart
        xAxis = new CategoryAxis();
        xAxis.setLabel(languageData.getString("20"));
        xAxis.getStyleClass().add("axis-label");
        xAxis.setId("tLabelX");

        yAxis = new NumberAxis();
        yAxis.setLabel(languageData.getString("21"));
        yAxis.getStyleClass().add("axis-label");
        yAxis.setId("tLabelY");

        BarChart barChart = new BarChart(xAxis, yAxis);
        barChart.setLegendVisible(false);
        barChart.getStyleClass().add("chart-plot-background");

        XYChart.Series<String, Number> xySeriesChart = new XYChart.Series<>();
        barChart.getData().add(xySeriesChart);

        VBox.setVgrow(barChart, Priority.ALWAYS);
        VBox chartBox = new VBox();
        chartBox.setAlignment(Pos.CENTER);
        chartBox.getChildren().add(barChart);

        // main labels margins
        VBox.setMargin(currentDate, new Insets(10, 10, 5, 5));
        VBox.setMargin(numberOfSessions, new Insets(5, 10, 5, 5));
        VBox.setMargin(lastSessionDuration, new Insets(5, 10, 5, 5));
        VBox.setMargin(longestSession, new Insets(5, 10, 5, 5));
        VBox.setMargin(totalTimeToday, new Insets(5, 10, 5, 5));
        VBox.setMargin(totalTimeThisWeek, new Insets(5, 10, 5, 5));

        // elapsed time labels margins
        VBox.setMargin(sessionStarted, new Insets(5, 10, 5, 5));
        VBox.setMargin(sessionFinished, new Insets(5, 10, 5, 5));
        VBox.setMargin(elapsedSessionTime, new Insets(5, 10, 5, 5));

        VBox.setVgrow(chartBox, Priority.ALWAYS);
        VBox borderBox = new VBox();
        borderBox.setAlignment(Pos.TOP_LEFT);
        borderBox.setStyle("-fx-border-style:solid; -fx-border-width:1; -fx-border-color:#c0c0c0;");
        borderBox.getChildren().addAll(mainLabels);

        // vBox for graphic buttons
        VBox.setMargin(mainData, new Insets(5));
        VBox.setMargin(elapsedTime, new Insets(5));
        VBox.setMargin(chartSelector, new Insets(5));
        VBox.setMargin(saveResults, new Insets(5));
        VBox.setMargin(loadResults, new Insets(5));
        VBox.setMargin(showNotes, new Insets(5));
        VBox toolBox = new VBox();
        toolBox.setAlignment(Pos.TOP_CENTER);
        toolBox.setStyle("-fx-border-style:solid; -fx-border-width:1; -fx-border-color:#c0c0c0;");
        toolBox.getChildren().addAll(mainData, elapsedTime, chartSelector, saveResults, loadResults, showNotes);

        // hBox to place ot center of the stage
        HBox.setHgrow(borderBox, Priority.ALWAYS);
        HBox.setMargin(borderBox, new Insets(0, 0, 0, 20));
        HBox.setMargin(toolBox, new Insets(0, 10, 0, 0));
        HBox centerBox = new HBox();
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setSpacing(5);
        centerBox.getChildren().addAll(borderBox, toolBox);

        // hBox to place the buttons
        HBox controlBox = new HBox();
        controlBox.setAlignment(Pos.CENTER);
        controlBox.setSpacing(15);
        controlBox.getChildren().addAll(startSession, clearDailyData, clearWeeklyData);

        mainData.setOnAction(event -> {
            borderBox.getChildren().clear();
            borderBox.getChildren().addAll(mainLabels);
        });

        elapsedTime.setOnAction(event -> {
            borderBox.getChildren().clear();
            borderBox.getChildren().addAll(progressLabels);
        });

        chartSelector.setOnAction(event -> {
            configureChartData(xySeriesChart, languageData, false);
            borderBox.getChildren().clear();
            borderBox.getChildren().addAll(chartBox);
        });

        saveResults.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle(languageData.getString("17"));
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
            fileChooser.setInitialFileName(LocalDate.now().toString() + ".txt");
            if (!preferences.get(SAVED_DATA_KEY, "").equals("")) {
                String dirPath = preferences.get(SAVED_DATA_KEY, "");
                fileChooser.setInitialDirectory(new File(dirPath.substring(0, dirPath.lastIndexOf('\\'))));
            }

            File file;
            try {
                file = fileChooser.showSaveDialog(primaryStage);
            } catch (Exception e) {
                fileChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
                file = fileChooser.showSaveDialog(primaryStage);
            }

            if (file == null)
                return;

            preferences.put(SAVED_DATA_KEY, file.getAbsolutePath());

            try (PrintWriter printWriter = new PrintWriter(new FileWriter(file))) {
                String data = languageData.getString("2") + preferences.getInt(SESSIONS_KEY, 0) + "\n" +
                        languageData.getString("3") + preferences.get(LAST_SESSION_KEY, "PT0s") + "\n" +
                        languageData.getString("4") + preferences.get(LONGEST_SESSION_KEY, "PT0s") + "\n" +
                        languageData.getString("5") + preferences.get(TOTAL_TIME_TODAY_KEY, "PT0s") + "\n" +
                        languageData.getString("6") + preferences.get(TOTAL_TIME_WEEK_KEY, "PT0s") + "\n";
                printWriter.print(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        loadResults.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle(languageData.getString("18"));
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
            if (!preferences.get(SAVED_DATA_KEY, "").equals("")) {
                String folderPath = preferences.get(SAVED_DATA_KEY, "");
                fileChooser.setInitialDirectory(new File(folderPath.substring(0, folderPath.lastIndexOf('\\'))));
            }
            File file;
            try {
                file = fileChooser.showOpenDialog(primaryStage);
            } catch (Exception e) {
                fileChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
                file = fileChooser.showOpenDialog(primaryStage);
            }

            if (file == null)
                return;

            preferences.put(SAVED_DATA_KEY, file.getAbsolutePath().replace('\'', '/'));

            List<String> data = new ArrayList<>();
            try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
                bufferedReader.lines().forEach(line -> data.add(line.substring(line.indexOf(":") + 1).trim()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            storePreferences(Duration.parse(data.get(3)), Duration.parse(data.get(4)), Integer.parseInt(data.get(0)),
                    data.get(2), Duration.parse(data.get(1)));

            numberOfSessions.setText(languageData.getString("2") + preferences.getInt(SESSIONS_KEY, 0));
            lastSessionDuration.setText(languageData.getString("3") + formatTime(preferences.get(LAST_SESSION_KEY, "PT0S")));
            longestSession.setText(languageData.getString("4") + formatTime(preferences.get(LONGEST_SESSION_KEY, "PT0S")));
            totalTimeToday.setText(languageData.getString("5") + formatTime(preferences.get(TOTAL_TIME_TODAY_KEY, "PT0S")));
            totalTimeThisWeek.setText(languageData.getString("6") + formatTime(preferences.get(TOTAL_TIME_WEEK_KEY, "PT0S")));

            loadChartData(file, xySeriesChart, languageData);
        });

        HBox notesHBox = new HBox();
        notesHBox.setAlignment(Pos.CENTER);

        TextArea notesTxtArea = new TextArea();
        notesTxtArea.setPrefColumnCount(20);
        notesTxtArea.setPrefRowCount(10);
        notesTxtArea.setFont(new Font("Serif", 17));
        notesTxtArea.setText(preferences.get("notes", "add some text..."));
        notesHBox.getChildren().add(notesTxtArea);

        HBox.setHgrow(notesTxtArea, Priority.ALWAYS);
        HBox.setMargin(notesTxtArea, new Insets(5));

        Stage notesStage = new Stage();
        notesStage.getIcons().add(new Image(getClass().getResourceAsStream("/notes.png")));
        Scene notesScene = new Scene(notesHBox, 600, 400);
        notesStage.setTitle(languageData.getString("25"));
        notesStage.setScene(notesScene);

        notesStage.setOnCloseRequest(windowEvent -> preferences.put("notes", notesTxtArea.getText()));

        showNotes.setOnAction(event -> notesStage.show());

        startSession.setOnAction(event -> {
            if (startSession.getText().equals(languageData.getString("7"))) {
                startSessionCounter(elapsedSessionTime, languageData);
                startSession.setText(languageData.getString("8"));
                startTime = LocalTime.now();
                sessionFinished.setText(languageData.getString("15"));
                sessionStarted.setText(languageData.getString("14") + startTime.format(DateTimeFormatter.ofPattern("HH:mm:ss a")));
            } else {
                sessionCounterAction.cancel(false);
                startSession.setText(languageData.getString("7"));
                endTime = LocalTime.now();
                sessionFinished.setText(languageData.getString("15") + endTime.format(DateTimeFormatter.ofPattern("HH:mm:ss a")));
                Duration session = Duration.between(startTime, endTime);
                totalThisWeek = Duration.parse(preferences.get(TOTAL_TIME_WEEK_KEY, "PT0S"));
                String total_today = preferences.get(TOTAL_TIME_TODAY_KEY, "PT0S");
                int sessions = preferences.getInt(SESSIONS_KEY, 0);
                String longest_session = preferences.get(LONGEST_SESSION_KEY, "PT0S");

                if (total_today.isEmpty()) {
                    totalToday = session;
                } else {
                    Duration previousSession = Duration.parse(total_today);
                    totalToday = session.plus(previousSession);
                }
                totalThisWeek = totalThisWeek.plus(session);
                storePreferences(totalToday, totalThisWeek, ++sessions, longest_session, session);
                numberOfSessions.setText(languageData.getString("2") + sessions);
                lastSessionDuration.setText(languageData.getString("3") + formatTime(session.toString()));
                longestSession.setText(languageData.getString("4") + (Duration.parse(longest_session).compareTo(session) > 0
                        ? formatTime(longest_session)
                        : formatTime(session.toString())));
                totalTimeToday.setText(languageData.getString("5") + formatTime(totalToday.toString()));
                totalTimeThisWeek.setText(languageData.getString("6") + formatTime(totalThisWeek.toString()));
                configureChartData(xySeriesChart, languageData, false);
            }
        });

        clearDailyData.setOnAction((event) -> {
            if (startSession.getText().equals(languageData.getString("7"))) {
                preferences.put(SESSIONS_KEY, "PT0S");
                preferences.put(LAST_SESSION_KEY, "PT0S");
                preferences.put(LONGEST_SESSION_KEY, "PT0S");
                preferences.put(TOTAL_TIME_TODAY_KEY, "PT0S");

                numberOfSessions.setText(languageData.getString("2") + preferences.getInt(SESSIONS_KEY, 0));
                lastSessionDuration.setText(languageData.getString("3") + formatTime(preferences.get(LAST_SESSION_KEY, "PT0S")));
                longestSession.setText(languageData.getString("4") + formatTime(preferences.get(LONGEST_SESSION_KEY, "PT0S")));
                totalTimeToday.setText(languageData.getString("5") + formatTime(preferences.get(TOTAL_TIME_TODAY_KEY, "PT0S")));
                totalTimeThisWeek.setText(languageData.getString("6") + formatTime(preferences.get(TOTAL_TIME_WEEK_KEY, "PT0S")));
                sessionStarted.setText(languageData.getString("14"));
                sessionFinished.setText(languageData.getString("15"));
                elapsedSessionTime.setText(languageData.getString("16"));
            }
        });

        clearWeeklyData.setOnAction((event) -> {
            if (startSession.getText().equals(languageData.getString("7"))) {
                try {
                    String folderPath = preferences.get(SAVED_DATA_KEY, "");
                    String notes = preferences.get("notes", "add some text...");
                    preferences.clear();
                    preferences.put(SAVED_DATA_KEY, folderPath);
                    preferences.put("notes", notes);
                } catch (BackingStoreException e) {
                    e.printStackTrace();
                }
                numberOfSessions.setText(languageData.getString("2") + preferences.getInt(SESSIONS_KEY, 0));
                lastSessionDuration.setText(languageData.getString("3") + formatTime(preferences.get(LAST_SESSION_KEY, "PT0S")));
                longestSession.setText(languageData.getString("4") + formatTime(preferences.get(LONGEST_SESSION_KEY, "PT0S")));
                totalTimeToday.setText(languageData.getString("5") + formatTime(preferences.get(TOTAL_TIME_TODAY_KEY, "PT0S")));
                totalTimeThisWeek.setText(languageData.getString("6") + formatTime(preferences.get(TOTAL_TIME_WEEK_KEY, "PT0S")));
                sessionStarted.setText(languageData.getString("14"));
                sessionFinished.setText(languageData.getString("15"));
                elapsedSessionTime.setText(languageData.getString("16"));
                configureChartData(xySeriesChart, languageData, true);
            }
        });

        BorderPane.setMargin(controlBox, new Insets(20, 0, 20, 0));
        BorderPane.setMargin(centerBox, new Insets(20, 20, 10, 10));

        BorderPane borderPane = new BorderPane();
        borderPane.setCenter(centerBox);
        borderPane.setBottom(controlBox);
        borderPane.setId("mainPane");

        Scene scene = new Scene(borderPane, WIDTH, HEIGHT);
        scene.getStylesheets().add(getClass().getResource("styles").toExternalForm());
        return scene;
    }

    private void storePreferences(Duration totalToday, Duration totalTimeThisWeek, int sessions, String longest_session, Duration session) {
        preferences.put(TOTAL_TIME_TODAY_KEY, totalToday.toString());
        preferences.put(TOTAL_TIME_WEEK_KEY, totalTimeThisWeek.toString());
        preferences.putInt(SESSIONS_KEY, sessions);
        preferences.put(LONGEST_SESSION_KEY,
                Duration.parse(longest_session).compareTo(session) > 0
                        ? longest_session
                        : session.toString());
        preferences.put(LAST_SESSION_KEY, session.toString());
        preferences.put(LocalDate.now().getDayOfWeek().toString(), totalToday.toString());
    }

    private String formatTime(String text) {
        Duration duration = Duration.parse(text);
        long s = duration.getSeconds();
        return (s / 3600 > 0) ? String.format("%dh, %01dm, %01ds", s / 3600, (s % 3600) / 60, (s % 60))
                : (s / 60 > 0) ? String.format("%01dm, %01ds", (s % 3600) / 60, (s % 60)) : String.format("%01ds", (s % 60));
    }

    private void startSessionCounter(Label text, ResourceBundle myBundle) {
        class Runner {
            Duration duration = Duration.ofSeconds(0);
            void runTask() {
                sessionCounterAction = executorService.scheduleAtFixedRate(() -> text.setText(myBundle.getString("16")
                        + formatTime((duration = duration.plusSeconds(1)).toString())), 0, 1, TimeUnit.SECONDS);
            }
        }
        new Runner().runTask();
    }

    private void configureChartData(XYChart.Series<String, Number> xySeriesChart, ResourceBundle languageData, boolean reset) {
        Arrays.stream(DayOfWeek.values())
                .forEach(dayOfWeek -> xySeriesChart.getData().add(new XYChart.Data(languageData.getString(dayOfWeek.toString()),
                        reset ? 0 : calculateTime(Duration.parse(preferences.get(dayOfWeek.toString(), "PT0S"))))));
        long maxSeconds = Arrays.stream(DayOfWeek.values())
                .map(dayOfWeek -> Duration.parse(preferences.get(dayOfWeek.toString(), "PT0S")))
                .mapToLong(Duration::getSeconds)
                .max().orElse(0);
        if (Duration.ofSeconds(maxSeconds).toMinutes() > 59) {
            yAxis.setLabel(languageData.getString("21") + "(" + languageData.getString("24") + ")");
        } else if (maxSeconds > 59) {
            yAxis.setLabel(languageData.getString("21") + "(" + languageData.getString("23") + ")");
        }

        for (XYChart.Data<String, Number> data : xySeriesChart.getData()) {
            Node bar = data.getNode();
            bar.getStyleClass().add("white-bar");
        }
    }

    private void loadChartData(File selected, XYChart.Series<String, Number> xySeriesChart, ResourceBundle languageData) {
        for (DayOfWeek day : DayOfWeek.values()) { // clear previous data
            preferences.put(day.toString(), "PT0S");
        }
        DayOfWeek selectedDay = LocalDate.parse(selected.getName().replace(".txt", "")).getDayOfWeek(); // get selected day
        Optional.ofNullable(selected.toPath().getParent()).ifPresent(dir -> {
            try {
                Files.list(dir).forEach(f -> {
                    try {
                        DayOfWeek dayOfWeek = LocalDate.parse(f.getFileName().toString().replace(".txt", "")).getDayOfWeek();
                        String studyTime = Files.lines(f).filter(l -> l.startsWith("Ukupno vreme ucenja danas: ") || l.startsWith("Total study time today: "))
                                .map(l -> l.substring(l.indexOf(": ") + 2)).collect(Collectors.joining());
                        if (dayOfWeek.getValue() <= selectedDay.getValue()) { // store only days of the week before the selected day
                            preferences.put(dayOfWeek.toString(), studyTime);
                        }
                    } catch (IOException e) {
                        showAlert(e.getClass().getSimpleName(), e.getMessage());
                    }
                });
            } catch (IOException e) {
                showAlert(e.getClass().getSimpleName(), e.getMessage());
            }
            configureChartData(xySeriesChart, languageData, false);
        });
    }

    private void showAlert(String headerTxt, String contentTxt) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("An Error Occurred!");
        alert.setHeaderText(headerTxt);
        alert.setContentText(contentTxt );
        alert.showAndWait();
    }

    private double calculateTime(Duration duration) {
        return duration.toMinutes() > 59 ? duration.toHours() + ((duration.toMinutes() - Duration.ofHours(duration.toHours())
                .toMinutes())) / 60d : duration.toMinutes() / 60d;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
