# Setting up JavaFX in IntelliJ for the Replica System UI

This guide will help you set up JavaFX in your IntelliJ project to run the Replica System UI.

## Step 1: Download JavaFX SDK

1. Download the JavaFX SDK from [JavaFX Downloads](https://gluonhq.com/products/javafx/)
   - Choose the appropriate version for your OS (Windows, macOS, Linux)
   - Select the JDK version that matches your project (e.g., JavaFX 17.0.2)

2. Extract the downloaded ZIP file to a location on your computer
   - Remember this location, as you'll need it for the setup

## Step 2: Add JavaFX Library to IntelliJ Project

1. Open your project in IntelliJ IDEA
2. Go to **File > Project Structure** (or press Ctrl+Alt+Shift+S)
3. Select **Libraries** in the left pane
4. Click the **+** button and select **Java**
5. Navigate to the location where you extracted the JavaFX SDK
6. Select the **lib** folder within the JavaFX SDK directory
7. Click **OK** to add the library
8. Name the library "JavaFX" and click **OK**
9. Make sure the library is checked for your module and click **Apply** then **OK**

## Step 3: Add VM Options for JavaFX

1. Select **Run > Edit Configurations** from the menu
2. Click the **+** button to add a new configuration and select **Application**
3. Name it "ReplicaSystemUI"
4. Set the **Main class** to: `ui.ReplicaSystemLauncher`
5. In the **VM options** field, add the following, replacing the path with your actual JavaFX SDK lib location:
   ```
   --module-path "C:\path\to\javafx-sdk\lib" --add-modules javafx.controls,javafx.fxml
   ```
6. Click **Apply** and **OK**

## Step 4: Add Required Dependencies

You'll need to add these additional dependencies to your project:

1. RabbitMQ Client: [amqp-client-5.16.0.jar](https://repo1.maven.org/maven2/com/rabbitmq/amqp-client/5.16.0/amqp-client-5.16.0.jar)
2. SLF4J API: [slf4j-api-1.7.36.jar](https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar)
3. SLF4J Simple: [slf4j-simple-1.7.36.jar](https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/1.7.36/slf4j-simple-1.7.36.jar)

To add these:
1. Download each JAR file
2. Go to **File > Project Structure > Libraries**
3. Click the **+** button and select **Java**
4. Navigate to each downloaded JAR file and add it
5. Click **Apply** and **OK**

## Step 5: Run the Application

1. Select the "ReplicaSystemUI" configuration from the dropdown in the toolbar
2. Click the Run button (or press Shift+F10)

## Troubleshooting

If you encounter any errors:

1. **Error: JavaFX runtime components are missing**
   - Double-check the VM options path to your JavaFX SDK

2. **NoClassDefFoundError for JavaFX classes**
   - Ensure the JavaFX library is properly added to your project
   - Check that the module path in VM options is correct

3. **Cannot find RabbitMQ classes**
   - Verify that the RabbitMQ client JAR is added to your project libraries

4. **Process keeps running after application close**
   - This is expected behavior - the application starts replica processes that need to be manually stopped
   - Use the "Stop All Replicas" button before closing the application
