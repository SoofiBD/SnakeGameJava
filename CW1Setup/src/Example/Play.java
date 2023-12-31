package Example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.Properties;
import java.util.stream.Collectors;
import Example.ImageUtil;

public class Play extends JFrame implements KeyListener {
    private JButton startButton, stopButton, pauseButton;
    private JPanel gamePanel;
    private Timer timer;

    private boolean isRunning, isPaused;
    private Snake snake;
    private Food food;
    private final int DELAY = 100;
    private Image backgroundImage, gameOverImage, startSceneImage;

    private java.util.List<PlayerScore> highScore = new ArrayList<>();
    private static final String HIGH_SCORE_FILE = "highscores.properties";

    private java.util.List<Rectangle> obstacles;
    private int selectedLevel = 1;
    private RedDot redDot;
    private long lastRedDotSpawnTime = 0;
    private long nextRedDotSpawnDelay;


    class RedDot {
        private Rectangle position;
        private boolean isActive;
        private Image redDotImage;
        private long lastSpawnTime;
        private static final long SPAWN_INTERVAL = 10000;
        private int dx = 2;
        private int dy = 2;

        public RedDot() {
            this.position = new Rectangle(0, 0, 20, 20);
            this.isActive = false;
            this.lastSpawnTime = System.currentTimeMillis();
            this.redDotImage = ImageUtil.images.get("red-dot");
        }

        public void spawn(int x, int y) {
            this.position.setLocation(x, y);
            this.isActive = true;
            this.lastSpawnTime = System.currentTimeMillis();
        }

        public void draw(Graphics g) {
            if (isActive) {
                g.drawImage(redDotImage, position.x, position.y, null);
            }
        }

        public boolean isActive() {
            return isActive;
        }

        public void update() {
            if (isActive) {

                position.x += dx;
                position.y += dy;

                if (position.x < 0 || position.x > gamePanel.getWidth() - position.width) {
                    dx = -dx;
                }
                if (position.y < 0 || position.y > gamePanel.getHeight() - position.height) {
                    dy = -dy;
                }

                if (System.currentTimeMillis() - lastSpawnTime > SPAWN_INTERVAL) {
                    isActive = false;
                }
            }
        }


        public boolean checkCollision(Snake snake) {

            return isActive && snake.getHead().intersects(position);
        }

        public void maybeSpawn() {
            if (!isActive && (System.currentTimeMillis() - lastSpawnTime > SPAWN_INTERVAL)) {
                Random rand = new Random();
                int x = rand.nextInt(800 / 20) * 20;
                int y = rand.nextInt(600 / 20) * 20;
                spawn(x, y);
            }
        }
    }


    public Play() {
        redDot = new RedDot();
        loadHighScore();
        initBackgroundImage();
        initializeUI();
        initializeGame();

    }

    private void loadHighScore() {
        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(HIGH_SCORE_FILE)) {
            properties.load(input);
            highScore = properties.stringPropertyNames().stream()
                    .map(name -> new PlayerScore(name, Integer.parseInt(properties.getProperty(name))))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.out.println("There is no High Scores");
        }
    }

    private void saveHighScores() {
        Properties properties = new Properties();
        for (PlayerScore score : highScore) {
            properties.setProperty(score.name, String.valueOf(score.score));
        }
        try (OutputStream output = new FileOutputStream(HIGH_SCORE_FILE)) {
            properties.store(output, "High Score");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkAndAddHighscore() {
        if (!isRunning && snake.getScore() > 0) {
            String playerName = JOptionPane.showInputDialog(this, "Enter your name:");
            if (playerName != null && !playerName.trim().isEmpty()) {
                highScore.add(new PlayerScore(playerName, snake.getScore()));
                Collections.sort(highScore);
                if (highScore.size() > 10) {
                    highScore.remove(highScore.size() - 1);
                }
                saveHighScores();
                showHighScore();
            }
        }
    }

    private void showHighScore() {
        String[] columnNames = {"Player's Name :", "Score"};
        Object[][] data = new Object[highScore.size()][2];
        for (int i = 0; i < highScore.size(); i++) {
            data[i][0] = highScore.get(i).name;
            data[i][1] = highScore.get(i).score;
        }

        JTable table = new JTable(data, columnNames);
        JScrollPane scrollPane = new JScrollPane(table);
        table.setFillsViewportHeight(true);

        table.setFont(new Font("Arial", Font.PLAIN, 14));
        table.setRowHeight(24);

        JDialog dialog = new JDialog(this, "High Scores", true);
        dialog.add(scrollPane);
        dialog.setSize(300, 200);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    class PlayerScore implements Comparable<PlayerScore> {
        String name;
        int score;

        PlayerScore(String name, int score) {
            this.name = name;
            this.score = score;
        }

        @Override
        public int compareTo(PlayerScore other) {
            return Integer.compare(other.score, this.score);
        }

        @Override
        public String toString() {
            return name + ": " + score;
        }
    }

    private void initBackgroundImage() {
        backgroundImage = ImageUtil.images.get("UI-background");
        gameOverImage = ImageUtil.images.get("game-scene-01");
        startSceneImage = ImageUtil.images.get("game-start-scene");
    }

    private void initializeUI() {
        startButton = new JButton("Start");
        stopButton = new JButton("Stop");
        pauseButton = new JButton("Pause");

        startButton.addActionListener(e -> startGame());
        stopButton.addActionListener(e -> stopGame());
        pauseButton.addActionListener(e -> pauseGame());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(pauseButton);

        gamePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                if (isRunning) {
                    g.drawImage(backgroundImage, 0, 0, this.getWidth(), this.getHeight(), this);
                    snake.draw(g);
                    food.draw(g);
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
                    g.setColor(Color.MAGENTA);
                    g.drawString("Score: " + snake.score, 20, 40);
                    drawObstacles(g);
                } else if (snake.getScore() > 0) {
                    g.drawImage(gameOverImage, 0, 0, this.getWidth(), this.getHeight(), this);
                    checkAndAddHighscore();
                } else {
                    g.drawImage(startSceneImage, 0, 0, this.getWidth(), this.getHeight(), this);
                }

                redDot.draw(g);
            }
        };

        gamePanel.setPreferredSize(new Dimension(800, 600));
        gamePanel.setFocusable(true);
        gamePanel.addKeyListener(this);

        this.add(gamePanel, BorderLayout.CENTER);
        this.add(buttonPanel, BorderLayout.SOUTH);
        this.pack();
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setVisible(true);
    }

    private void initializeGame() {
        snake = new Snake();
        food = new Food();

        isRunning = false;
        isPaused = false;

        timer = new Timer(DELAY, e -> gameUpdate());
        timer.start();

        resetRedDotSpawnTimer();
        selectLevel();
    }

    private void resetRedDotSpawnTimer() {
        Random rand = new Random();
        nextRedDotSpawnDelay = 10000 + rand.nextInt(20000);
        lastRedDotSpawnTime = System.currentTimeMillis();
    }

    private void maybeSpawnRedDot() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRedDotSpawnTime >= nextRedDotSpawnDelay) {
            Random rand = new Random();
            int startX = rand.nextInt(gamePanel.getWidth());
            int startY = rand.nextInt(gamePanel.getHeight());
            int targetX = rand.nextInt(gamePanel.getWidth());
            int targetY = rand.nextInt(gamePanel.getHeight());

            redDot.spawn(startX,startY);
            resetRedDotSpawnTimer();
        }
    }

    private void selectLevel() {
        String[] options = {"Level 1", "Level 2", "Level 3"};
        int choice = JOptionPane.showOptionDialog(null, "Select the Level", "Level Selection",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);

        if (choice != -1) {
            selectedLevel = choice + 1;
            loadObstaclesForLevel(selectedLevel);
        } else {
            System.exit(0);
        }
    }

    private void loadObstaclesForLevel(int level) {
        obstacles = new ArrayList<>();
        Random random = new Random();
        int numberOfObstacles = 8;
        int minDistanceFromSnake = 100;

        while (obstacles.size() < numberOfObstacles) {
            int x = random.nextInt(800 / 20) * 20;
            int y = random.nextInt(600 / 20) * 20;

            if (Math.abs(x - snake.getHead().x) > minDistanceFromSnake &&
                    Math.abs(y - snake.getHead().y) > minDistanceFromSnake) {

                Rectangle newObstacle = new Rectangle(x, y, 20, 20);
                boolean collides = false;

                for (Rectangle existingObstacle : obstacles) {
                    if (newObstacle.intersects(existingObstacle)) {
                        collides = true;
                        break;
                    }
                }

                if (!collides) {
                    obstacles.add(newObstacle);
                }
            }
        }
    }

    private void checkCollisionWithObstacles() {
        Rectangle snakeHead = snake.getHead();
        for (Rectangle obstacle : obstacles) {
            Rectangle hitbox = new Rectangle(
                    obstacle.x - 1,
                    obstacle.y - 1,
                    obstacle.width + 2,
                    obstacle.height + 2
            );

            if (snakeHead.intersects(obstacle)) {
                isRunning = false;
                stopGame();
                checkAndAddHighscore();
                return;
            }
        }
    }

    private void drawObstacles(Graphics g) {
        for (Rectangle obstacle : obstacles) {
            Image obstacleImage = ImageUtil.images.get("brick-" + selectedLevel);
            g.drawImage(obstacleImage, obstacle.x, obstacle.y, this);
        }
    }

    private void gameUpdate() {
        if (isRunning && !isPaused) {
            snake.move();
            checkCollision();
            checkCollisionWithObstacles();
            maybeSpawnRedDot();
            redDot.update();
            checkRedDotCollision();
            gamePanel.repaint();
        }
        /*if (!isRunning) {
            checkAndAddHighscore();
        }*/
    }

    private void checkRedDotCollision() {
        if (redDot.isActive() && redDot.checkCollision(snake)) {
            isRunning = false;
            redDot.isActive = false;
            stopGame();
        }
    }

    private void startGame() {
        isRunning = true;
        isPaused = false;
        snake.reset();
        gamePanel.requestFocusInWindow();
        resetRedDotSpawnTimer();
    }

    private void stopGame() {
        checkAndAddHighscore();
        isRunning = false;
        snake.reset();
        redDot.isActive=false;
        SwingUtilities.invokeLater(() -> gamePanel.requestFocusInWindow());
    }

    private void pauseGame() {
        if (isRunning) {
            isPaused = !isPaused;
        }
    }

    private void checkCollision() {
        if (snake.getHead().intersects(food.getPosition())) {
            snake.grow();
            food.reposition();
        }
        if (snake.checkCollision()) {
            isRunning = false;
            stopGame();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        snake.keyPressed(e);
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Play());
    }

    class Snake {
        private ArrayList<Rectangle> body;
        private int direction = KeyEvent.VK_RIGHT;
        private int size = 3;
        private int score = 0;
        private Image headImage, bodyImage;

        public Snake() {
            loadImages();
            reset();
        }

        private void loadImages() {
            headImage = ImageUtil.images.get("snake-head-right");
            bodyImage = ImageUtil.images.get("snake-body");
        }

        public void reset() {
            body = new ArrayList<>();
            size = 3;
            score = 0;
            for (int i = 0; i < size; i++) {
                body.add(new Rectangle(300 - i * 20, 300, 20, 20));
            }
            direction = KeyEvent.VK_RIGHT;
        }

        public void move() {
            Rectangle head = new Rectangle(body.get(0));
            switch (direction) {
                case KeyEvent.VK_UP:
                    head.y -= 20;
                    break;
                case KeyEvent.VK_DOWN:
                    head.y += 20;
                    break;
                case KeyEvent.VK_LEFT:
                    head.x -= 20;
                    break;
                case KeyEvent.VK_RIGHT:
                    head.x += 20;
                    break;
            }
            if (head.x < 0 || head.y < 0 || head.x >= gamePanel.getWidth() || head.y >= gamePanel.getHeight()) {
                isRunning = false;
                return;
            }
            body.add(0, head);
            if (body.size() > size) {
                body.remove(body.size() - 1);
            }
        }

        public void grow() {
            size++;
            score += 10;
            food.reposition();
        }

        public void draw(Graphics g) {
            for (int i = 0; i < body.size(); i++) {
                Image image = i == 0 ? headImage : bodyImage;
                g.drawImage(image, body.get(i).x, body.get(i).y, null);
            }
        }

        public Rectangle getHead() {
            return body.get(0);
        }

        public boolean checkCollision() {
            for (int i = 1; i < body.size(); i++) {
                if (body.get(i).intersects(getHead())) {
                    isRunning = false;
                    stopGame(); // Oyunu durdur ve skor tablosunu göster
                    return true;
                }
            }
            return false;
        }

        public void keyPressed(KeyEvent e) {
            if ((e.getKeyCode() == KeyEvent.VK_LEFT && direction != KeyEvent.VK_RIGHT) ||
                    (e.getKeyCode() == KeyEvent.VK_RIGHT && direction != KeyEvent.VK_LEFT) ||
                    (e.getKeyCode() == KeyEvent.VK_UP && direction != KeyEvent.VK_DOWN) ||
                    (e.getKeyCode() == KeyEvent.VK_DOWN && direction != KeyEvent.VK_UP)) {
                direction = e.getKeyCode();
            }
        }

        public int getScore() {
            return score;
        }
    }

    class Food {
        private Rectangle position;
        private Image foodImage;
        private Random rand = new Random();

        public Food() {
            reposition();
        }

        public void reposition() {
            String key = String.valueOf(rand.nextInt(17));
            foodImage = ImageUtil.images.get(key);
            position = new Rectangle(rand.nextInt(800 / 20) * 20, rand.nextInt(600 / 20) * 20, 20, 20);
        }

        public void draw(Graphics g) {
            g.drawImage(foodImage, position.x, position.y, null);
        }

        public Rectangle getPosition() {
            return position;
        }
    }
}