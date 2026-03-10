import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Main {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> {
            SudokuFrame frame = new SudokuFrame();
            frame.setVisible(true);
        });
    }
}

class SudokuFrame extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final int SIZE = 9;
    private static final int EMPTY = 0;
    private static final int BASE_WIDTH = 860;
    private static final int BASE_HEIGHT = 980;
    private static final double FRAME_RATIO = (double) BASE_HEIGHT / BASE_WIDTH;
    private static final Color APP_BG = new Color(241, 237, 250);
    private static final Color PANEL_BG = new Color(232, 224, 245);
    private static final Color FIXED_BG = new Color(214, 203, 234);
    private static final Color CELL_BG = new Color(252, 249, 255);
    private static final Color GROUP_BG = new Color(239, 233, 249);
    private static final Color SELECT_BG = new Color(220, 201, 252);
    private static final Color MEMO_BG = new Color(245, 239, 252);
    private static final Color USER_FG = new Color(84, 50, 138);
    private static final Color HINT_FG = new Color(32, 101, 209);
    private static final Color MEMO_FG = new Color(102, 82, 132);
    private static final Color CONFLICT_FG = new Color(198, 40, 40);
    private static final Color BORDER_NORMAL = new Color(93, 70, 130);
    private static final Color BORDER_GROUP = new Color(132, 106, 173);
    private static final Color BORDER_SELECTED = new Color(168, 118, 232);
    private static final Color FIXED_FG = new Color(52, 36, 82);

    private final JLabel[][] cells = new JLabel[SIZE][SIZE];
    private final int[][] puzzle = new int[SIZE][SIZE];
    private final int[][] solution = new int[SIZE][SIZE];
    private final int[][] userValues = new int[SIZE][SIZE];
    private final boolean[][] fixed = new boolean[SIZE][SIZE];
    private final boolean[][] hinted = new boolean[SIZE][SIZE];
    private final boolean[][][] memos = new boolean[SIZE][SIZE][10];
    private final boolean[][] valueConflictByMemo = new boolean[SIZE][SIZE];
    private final boolean[][][] memoDigitConflicts = new boolean[SIZE][SIZE][10];

    private final JLabel statusLabel = new JLabel("새 게임을 시작하세요.", SwingConstants.CENTER);
    private final JLabel guideLabel = new JLabel(
            "조작: 마우스/방향키로 선택, 1~9 입력, Backspace 지우기, 메모 모드에서 후보 토글",
            SwingConstants.CENTER
    );
    private final JLabel lastResultLabel = new JLabel("최근 기록: 없음", SwingConstants.CENTER);
    private final JLabel stageLabel = new JLabel("", SwingConstants.CENTER);
    private final Random random = new Random();
    private final transient Map<String, Integer> clearedStagesByMode = new HashMap<>();

    private final Font valueFont = new Font("Dialog", Font.BOLD, 26);
    private final Font memoFont = new Font("Consolas", Font.PLAIN, 13);
    private boolean memoModeEnabled = false;
    private int selectedRow = -1;
    private int selectedCol = -1;
    private long stageStartTimeMillis = 0L;
    private int hintUsedCount = 0;
    private boolean stageCleared = false;
    private boolean resizingGuard = false;

    private final JComboBox<String> difficultyBox = new JComboBox<>(new String[]{
            "이지", "노멀", "하드"
    });

    SudokuFrame() {
        setTitle("스도쿠");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(BASE_WIDTH, BASE_HEIGHT);
        setMinimumSize(new Dimension(720, Math.round(720f * (float) FRAME_RATIO)));
        setResizable(true);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(APP_BG);

        JPanel boardPanel = createBoardPanel();
        JPanel boardContainer = createSquareBoardContainer(boardPanel);
        JPanel controlPanel = createControlPanel();
        statusLabel.setFont(new Font("Dialog", Font.PLAIN, 16));
        guideLabel.setFont(new Font("Dialog", Font.PLAIN, 13));
        statusLabel.setOpaque(true);
        guideLabel.setOpaque(true);
        lastResultLabel.setOpaque(true);
        stageLabel.setOpaque(true);
        statusLabel.setBackground(PANEL_BG);
        guideLabel.setBackground(PANEL_BG);
        lastResultLabel.setBackground(PANEL_BG);
        stageLabel.setBackground(PANEL_BG);
        lastResultLabel.setFont(new Font("Dialog", Font.PLAIN, 13));
        stageLabel.setFont(new Font("Dialog", Font.BOLD, 13));

        JPanel topPanel = new JPanel(new GridLayout(4, 1));
        topPanel.setBackground(PANEL_BG);
        topPanel.add(statusLabel);
        topPanel.add(guideLabel);
        topPanel.add(lastResultLabel);
        topPanel.add(stageLabel);

        add(boardContainer, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);
        clearedStagesByMode.put("이지", 0);
        clearedStagesByMode.put("노멀", 0);
        clearedStagesByMode.put("하드", 0);
        installAspectRatioResizeHandler();
        installGlobalKeyHandler();
        updateStageSummaryLabel();

        startNewGame();
    }

    private void installAspectRatioResizeHandler() {
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (resizingGuard) {
                    return;
                }
                resizingGuard = true;
                int width = Math.max(getWidth(), 720);
                int targetHeight = (int) Math.round(width * FRAME_RATIO);
                setSize(width, targetHeight);
                resizingGuard = false;
            }
        });
    }

    private void installGlobalKeyHandler() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(event -> {
            if (event.getID() != KeyEvent.KEY_PRESSED) {
                return false;
            }
            if (!isFocused() || selectedRow < 0 || selectedCol < 0) {
                return false;
            }
            handleCellKeyInput(selectedRow, selectedCol, event);
            return true;
        });
    }

    private JPanel createBoardPanel() {
        JPanel boardPanel = new JPanel(new GridLayout(SIZE, SIZE));
        boardPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        boardPanel.setBackground(PANEL_BG);

        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                JLabel cell = new JLabel("", SwingConstants.CENTER);
                cell.setFocusable(true);
                cell.setRequestFocusEnabled(true);
                cell.setFocusTraversalKeysEnabled(false);
                cell.setOpaque(true);
                cell.setVerticalAlignment(SwingConstants.CENTER);
                cell.setHorizontalAlignment(SwingConstants.CENTER);

                int top = (r % 3 == 0) ? 3 : 1;
                int left = (c % 3 == 0) ? 3 : 1;
                int bottom = (r == SIZE - 1) ? 3 : 1;
                int right = (c == SIZE - 1) ? 3 : 1;
                cell.setBorder(BorderFactory.createMatteBorder(top, left, bottom, right, BORDER_NORMAL));

                final int row = r;
                final int col = c;
                cell.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        selectAndFocusCell(row, col);
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        selectAndFocusCell(row, col);
                    }

                    @Override
                    public void mouseClicked(MouseEvent e) {
                        selectAndFocusCell(row, col);
                    }
                });
                cell.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent e) {
                        handleCellKeyInput(row, col, e);
                    }
                });

                cells[r][c] = cell;
                boardPanel.add(cell);
            }
        }
        return boardPanel;
    }

    private JPanel createSquareBoardContainer(JPanel boardPanel) {
        JPanel container = new JPanel(null) {
            @Override
            public void doLayout() {
                int size = Math.min(getWidth(), getHeight());
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                boardPanel.setBounds(x, y, size, size);
            }
        };
        container.setBackground(APP_BG);
        container.add(boardPanel);
        return container;
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 6, 8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        panel.setBackground(PANEL_BG);

        JButton newGameButton = new JButton("새 게임");
        JToggleButton memoToggleButton = new JToggleButton("메모 모드: 해제");
        JButton hintButton = new JButton("힌트");
        JButton checkButton = new JButton("검사");
        JButton resetButton = new JButton("초기화");

        difficultyBox.setSelectedItem("노멀");
        styleControl(newGameButton);
        styleControl(memoToggleButton);
        styleControl(hintButton);
        styleControl(checkButton);
        styleControl(resetButton);
        styleControl(difficultyBox);

        newGameButton.addActionListener(e -> startNewGame());
        memoToggleButton.addActionListener(e -> {
            memoModeEnabled = memoToggleButton.isSelected();
            memoToggleButton.setText(memoModeEnabled
                    ? "메모 모드: 사용"
                    : "메모 모드: 해제");
            statusLabel.setText(memoModeEnabled
                    ? "메모 모드: 1~9를 누르면 후보가 추가/제거됩니다."
                    : "값 입력 모드: 1~9를 누르면 정식 값이 들어갑니다.");
            renderAllCells();
        });
        hintButton.addActionListener(e -> revealHint());
        checkButton.addActionListener(e -> checkBoard());
        resetButton.addActionListener(e -> resetUserInputs());

        panel.add(newGameButton);
        panel.add(difficultyBox);
        panel.add(memoToggleButton);
        panel.add(hintButton);
        panel.add(checkButton);
        panel.add(resetButton);
        return panel;
    }

    private void styleControl(javax.swing.JComponent component) {
        component.setFont(new Font("Dialog", Font.BOLD, 14));
        component.setBackground(new Color(247, 241, 255));
        component.setForeground(new Color(71, 50, 104));
        component.setBorder(BorderFactory.createLineBorder(new Color(140, 112, 183), 1));

        if (component instanceof javax.swing.AbstractButton) {
            ((javax.swing.AbstractButton) component).setHorizontalAlignment(SwingConstants.CENTER);
        }
        if (component instanceof JComboBox<?>) {
            ((JLabel) ((JComboBox<?>) component).getRenderer()).setHorizontalAlignment(SwingConstants.CENTER);
        }
    }

    private void handleCellKeyInput(int row, int col, KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_LEFT) {
            moveSelection(0, -1);
            return;
        }
        if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
            moveSelection(0, 1);
            return;
        }
        if (e.getKeyCode() == KeyEvent.VK_UP) {
            moveSelection(-1, 0);
            return;
        }
        if (e.getKeyCode() == KeyEvent.VK_DOWN) {
            moveSelection(1, 0);
            return;
        }

        if (fixed[row][col]) {
            return;
        }

        int digit = getDigitFromKey(e);
        if (digit >= 1 && digit <= 9) {
            if (memoModeEnabled) {
                userValues[row][col] = EMPTY;
                memos[row][col][digit] = !memos[row][col][digit];
            } else {
                userValues[row][col] = digit;
            }
            recomputeMemoConflicts();
            renderAllCells();
            return;
        }

        if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE
                || e.getKeyCode() == KeyEvent.VK_DELETE
                || e.getKeyCode() == KeyEvent.VK_0
                || e.getKeyCode() == KeyEvent.VK_NUMPAD0
                || e.getKeyCode() == KeyEvent.VK_SPACE) {
            if (userValues[row][col] != EMPTY) {
                userValues[row][col] = EMPTY;
            } else {
                clearMemos(row, col);
            }
            recomputeMemoConflicts();
            renderAllCells();
        }
    }

    private int getDigitFromKey(KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (keyCode >= KeyEvent.VK_1 && keyCode <= KeyEvent.VK_9) {
            return keyCode - KeyEvent.VK_0;
        }
        if (keyCode >= KeyEvent.VK_NUMPAD1 && keyCode <= KeyEvent.VK_NUMPAD9) {
            return keyCode - KeyEvent.VK_NUMPAD0;
        }
        return -1;
    }

    private void startNewGame() {
        int[][] generated = new int[SIZE][SIZE];
        fillBoard(generated);
        copyBoard(generated, solution);
        buildPuzzleFromSolution();
        clearUserState();
        setSelectedCell(0, 0);
        recomputeMemoConflicts();
        renderAllCells();
        stageStartTimeMillis = System.currentTimeMillis();
        hintUsedCount = 0;
        stageCleared = false;

        String difficulty = (String) difficultyBox.getSelectedItem();
        int nextStage = clearedStagesByMode.getOrDefault(difficulty, 0) + 1;
        statusLabel.setText("새 게임 시작: " + difficulty + " " + nextStage + "스테이지 | 힌트는 선택한 칸에 적용");
    }

    private void buildPuzzleFromSolution() {
        copyBoard(solution, puzzle);
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                fixed[r][c] = true;
                hinted[r][c] = false;
            }
        }

        int blanks = getBlankCountByDifficulty();
        List<int[]> cellsToTry = new ArrayList<>();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                cellsToTry.add(new int[]{r, c});
            }
        }
        Collections.shuffle(cellsToTry, random);

        int removed = 0;
        for (int[] pos : cellsToTry) {
            if (removed >= blanks) {
                break;
            }
            int r = pos[0];
            int c = pos[1];
            int backup = puzzle[r][c];
            puzzle[r][c] = EMPTY;

            int[][] testBoard = new int[SIZE][SIZE];
            copyBoard(puzzle, testBoard);
            int solutionCount = countSolutions(testBoard, 2);
            boolean logicSolvable = solutionCount == 1 && canSolveByLogicOnly(testBoard);
            if (logicSolvable) {
                fixed[r][c] = false;
                hinted[r][c] = false;
                removed++;
            } else {
                puzzle[r][c] = backup;
            }
        }
    }

    private int getBlankCountByDifficulty() {
        String difficulty = (String) difficultyBox.getSelectedItem();
        if ("이지".equals(difficulty)) {
            return 38;
        }
        if ("하드".equals(difficulty)) {
            return 54;
        }
        return 46;
    }

    private void clearUserState() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                userValues[r][c] = EMPTY;
                clearMemos(r, c);
            }
        }
        recomputeMemoConflicts();
    }

    private void clearMemos(int row, int col) {
        for (int d = 1; d <= 9; d++) {
            memos[row][col][d] = false;
        }
    }

    private void renderAllCells() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                renderCell(r, c);
            }
        }
    }

    private void renderCell(int row, int col) {
        JLabel cell = cells[row][col];
        boolean isSelected = row == selectedRow && col == selectedCol;
        boolean sameGroup = selectedRow != -1 && selectedCol != -1
                && (row == selectedRow || col == selectedCol || isSameBox(row, col, selectedRow, selectedCol));
        cell.setBorder(createCellBorder(row, col, isSelected, sameGroup));

        if (fixed[row][col]) {
            Color bg = isSelected ? SELECT_BG : FIXED_BG;
            Color fg;
            if (!memoModeEnabled && valueConflictByMemo[row][col]) {
                fg = CONFLICT_FG;
            } else {
                fg = hinted[row][col] ? HINT_FG : FIXED_FG;
            }
            setCellText(cell, String.valueOf(puzzle[row][col]), valueFont, fg, bg, false);
            return;
        }

        int value = userValues[row][col];
        if (value != EMPTY) {
            Color bg = isSelected ? SELECT_BG : (sameGroup ? GROUP_BG : CELL_BG);
            Color fg = (!memoModeEnabled && valueConflictByMemo[row][col]) ? CONFLICT_FG : USER_FG;
            setCellText(cell, String.valueOf(value), valueFont, fg, bg, false);
            return;
        }

        String memoGrid = buildMemoGridHtml(row, col);
        if (!memoGrid.isEmpty()) {
            Color bg = isSelected ? SELECT_BG : MEMO_BG;
            setCellText(cell, memoGrid, memoFont, MEMO_FG, bg, true);
        } else {
            Color bg = isSelected ? SELECT_BG : (sameGroup ? GROUP_BG : CELL_BG);
            setCellText(cell, "", memoFont, MEMO_FG, bg, false);
        }
    }

    private void setSelectedCell(int row, int col) {
        selectedRow = row;
        selectedCol = col;
        renderAllCells();
    }

    private void selectAndFocusCell(int row, int col) {
        setSelectedCell(row, col);
        SwingUtilities.invokeLater(() -> cells[row][col].requestFocusInWindow());
    }

    private void moveSelection(int dRow, int dCol) {
        if (selectedRow == -1 || selectedCol == -1) {
            setSelectedCell(0, 0);
            cells[0][0].requestFocusInWindow();
            return;
        }
        int nextRow = Math.max(0, Math.min(SIZE - 1, selectedRow + dRow));
        int nextCol = Math.max(0, Math.min(SIZE - 1, selectedCol + dCol));
        setSelectedCell(nextRow, nextCol);
        cells[nextRow][nextCol].requestFocusInWindow();
    }

    private boolean isSameBox(int r1, int c1, int r2, int c2) {
        return (r1 / 3) == (r2 / 3) && (c1 / 3) == (c2 / 3);
    }

    private javax.swing.border.Border createCellBorder(int row, int col, boolean selected, boolean sameGroup) {
        int top = (row % 3 == 0) ? 3 : 1;
        int left = (col % 3 == 0) ? 3 : 1;
        int bottom = (row == SIZE - 1) ? 3 : 1;
        int right = (col == SIZE - 1) ? 3 : 1;
        Color color = Color.BLACK;
        if (sameGroup) {
            color = BORDER_GROUP;
        }
        if (selected) {
            color = BORDER_SELECTED;
        }
        if (!selected && !sameGroup) {
            color = BORDER_NORMAL;
        }
        return BorderFactory.createMatteBorder(top, left, bottom, right, color);
    }

    private void setCellText(JLabel cell, String text, Font font, Color fg, Color bg, boolean memo) {
        cell.setFont(font);
        cell.setForeground(fg);
        cell.setBackground(bg);
        if (memo) {
            cell.setText("<html><div style='text-align:center; line-height:1.2;'>" + text + "</div></html>");
        } else {
            cell.setText(text);
        }
    }

    private String buildMemoGridHtml(int row, int col) {
        boolean hasMemo = false;
        StringBuilder sb = new StringBuilder();
        for (int blockRow = 0; blockRow < 3; blockRow++) {
            for (int blockCol = 0; blockCol < 3; blockCol++) {
                int digit = blockRow * 3 + blockCol + 1;
                if (memos[row][col][digit]) {
                    sb.append(digit);
                    hasMemo = true;
                } else {
                    sb.append('.');
                }
                if (blockCol < 2) {
                    sb.append(' ');
                }
            }
            if (blockRow < 2) {
                sb.append("<br>");
            }
        }
        return hasMemo ? sb.toString() : "";
    }

    private void recomputeMemoConflicts() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                valueConflictByMemo[r][c] = false;
                for (int d = 1; d <= 9; d++) {
                    memoDigitConflicts[r][c][d] = false;
                }
            }
        }

        for (int row = 0; row < SIZE; row++) {
            markUnitPlacedConflicts(getRowUnit(row));
            markUnitMemoConflicts(getRowUnit(row));
        }
        for (int col = 0; col < SIZE; col++) {
            markUnitPlacedConflicts(getColUnit(col));
            markUnitMemoConflicts(getColUnit(col));
        }
        for (int boxRow = 0; boxRow < 3; boxRow++) {
            for (int boxCol = 0; boxCol < 3; boxCol++) {
                List<int[]> unit = getBoxUnit(boxRow * 3, boxCol * 3);
                markUnitPlacedConflicts(unit);
                markUnitMemoConflicts(unit);
            }
        }
    }

    private void markUnitPlacedConflicts(List<int[]> unit) {
        for (int digit = 1; digit <= 9; digit++) {
            List<int[]> placed = new ArrayList<>();
            for (int[] rc : unit) {
                if (getCurrentValue(rc[0], rc[1]) == digit) {
                    placed.add(rc);
                }
            }
            if (placed.size() > 1) {
                for (int[] rc : placed) {
                    valueConflictByMemo[rc[0]][rc[1]] = true;
                }
            }
        }
    }

    private List<int[]> getRowUnit(int row) {
        List<int[]> unit = new ArrayList<>();
        for (int c = 0; c < SIZE; c++) {
            unit.add(new int[]{row, c});
        }
        return unit;
    }

    private List<int[]> getColUnit(int col) {
        List<int[]> unit = new ArrayList<>();
        for (int r = 0; r < SIZE; r++) {
            unit.add(new int[]{r, col});
        }
        return unit;
    }

    private List<int[]> getBoxUnit(int startRow, int startCol) {
        List<int[]> unit = new ArrayList<>();
        for (int r = startRow; r < startRow + 3; r++) {
            for (int c = startCol; c < startCol + 3; c++) {
                unit.add(new int[]{r, c});
            }
        }
        return unit;
    }

    private void markUnitMemoConflicts(List<int[]> unit) {
        for (int digit = 1; digit <= 9; digit++) {
            List<int[]> placed = new ArrayList<>();
            List<int[]> memoed = new ArrayList<>();

            for (int[] rc : unit) {
                int r = rc[0];
                int c = rc[1];
                if (getCurrentValue(r, c) == digit) {
                    placed.add(rc);
                }
                if (memos[r][c][digit]) {
                    memoed.add(rc);
                }
            }

            if (memoed.isEmpty()) {
                continue;
            }

            boolean conflict = !placed.isEmpty() || memoed.size() > 1;
            if (!conflict) {
                continue;
            }

            for (int[] rc : memoed) {
                memoDigitConflicts[rc[0]][rc[1]][digit] = true;
            }
        }
    }

    private void revealHint() {
        if (selectedRow == -1 || selectedCol == -1) {
            statusLabel.setText("힌트를 받을 칸을 먼저 클릭해 선택하세요.");
            return;
        }
        int row = selectedRow;
        int col = selectedCol;

        if (fixed[row][col]) {
            statusLabel.setText("선택한 칸은 이미 고정 숫자입니다.");
            return;
        }

        puzzle[row][col] = solution[row][col];
        fixed[row][col] = true;
        hinted[row][col] = true;
        userValues[row][col] = EMPTY;
        clearMemos(row, col);
        hintUsedCount++;
        recomputeMemoConflicts();
        renderAllCells();
        statusLabel.setText("선택한 칸에 힌트 적용 (누적 " + hintUsedCount + "회)");
    }

    private int getCurrentValue(int row, int col) {
        if (fixed[row][col]) {
            return puzzle[row][col];
        }
        return userValues[row][col];
    }

    private void checkBoard() {
        int[][] current = new int[SIZE][SIZE];
        boolean hasEmpty = false;

        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                int value = getCurrentValue(r, c);
                current[r][c] = value;
                if (value == EMPTY) {
                    hasEmpty = true;
                }
            }
        }

        if (!isBoardStateValid(current)) {
            statusLabel.setText("현재 보드에 규칙 위반이 있습니다.");
            return;
        }

        if (hasEmpty) {
            statusLabel.setText("아직 완성되지 않았습니다.");
            return;
        }

        boolean solved = true;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (current[r][c] != solution[r][c]) {
                    solved = false;
                    break;
                }
            }
        }

        if (solved) {
            if (!stageCleared) {
                stageCleared = true;
                String difficulty = (String) difficultyBox.getSelectedItem();
                int cleared = clearedStagesByMode.getOrDefault(difficulty, 0) + 1;
                clearedStagesByMode.put(difficulty, cleared);
                updateStageSummaryLabel();
            }
            long elapsedSec = Math.max(0L, (System.currentTimeMillis() - stageStartTimeMillis) / 1000L);
            lastResultLabel.setText(
                    "최근 기록: 소요 " + formatDuration(elapsedSec) + " | 힌트 " + hintUsedCount + "회"
            );
            statusLabel.setText("정답입니다. 축하합니다!");
            JOptionPane.showMessageDialog(
                    this,
                    "정답입니다! 축하합니다.\n"
                            + "소요 시간: " + formatDuration(elapsedSec) + "\n"
                            + "힌트 사용: " + hintUsedCount + "회",
                    "완료",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } else {
            statusLabel.setText("아직 정답이 아닙니다.");
        }
    }

    private boolean isBoardStateValid(int[][] board) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                int value = board[r][c];
                if (value != EMPTY && !isPlacementValid(board, r, c, value)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isPlacementValid(int[][] board, int row, int col, int value) {
        for (int i = 0; i < SIZE; i++) {
            if (i != col && board[row][i] == value) {
                return false;
            }
            if (i != row && board[i][col] == value) {
                return false;
            }
        }
        int startRow = (row / 3) * 3;
        int startCol = (col / 3) * 3;
        for (int r = startRow; r < startRow + 3; r++) {
            for (int c = startCol; c < startCol + 3; c++) {
                if ((r != row || c != col) && board[r][c] == value) {
                    return false;
                }
            }
        }
        return true;
    }

    private void resetUserInputs() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (!fixed[r][c]) {
                    userValues[r][c] = EMPTY;
                    clearMemos(r, c);
                    renderCell(r, c);
                }
            }
        }
        recomputeMemoConflicts();
        renderAllCells();
        statusLabel.setText("입력값과 메모가 초기화되었습니다.");
    }

    private boolean fillBoard(int[][] board) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] == EMPTY) {
                    List<Integer> candidates = createShuffledNumbers();
                    for (int value : candidates) {
                        if (canPlace(board, r, c, value)) {
                            board[r][c] = value;
                            if (fillBoard(board)) {
                                return true;
                            }
                            board[r][c] = EMPTY;
                        }
                    }
                    return false;
                }
            }
        }
        return true;
    }

    private List<Integer> createShuffledNumbers() {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 1; i <= SIZE; i++) {
            numbers.add(i);
        }
        Collections.shuffle(numbers, random);
        return numbers;
    }

    private boolean canPlace(int[][] board, int row, int col, int value) {
        for (int i = 0; i < SIZE; i++) {
            if (board[row][i] == value || board[i][col] == value) {
                return false;
            }
        }
        int startRow = (row / 3) * 3;
        int startCol = (col / 3) * 3;
        for (int r = startRow; r < startRow + 3; r++) {
            for (int c = startCol; c < startCol + 3; c++) {
                if (board[r][c] == value) {
                    return false;
                }
            }
        }
        return true;
    }

    private void copyBoard(int[][] from, int[][] to) {
        for (int r = 0; r < SIZE; r++) {
            System.arraycopy(from[r], 0, to[r], 0, SIZE);
        }
    }

    private boolean canSolveByLogicOnly(int[][] board) {
        int[][] work = new int[SIZE][SIZE];
        copyBoard(board, work);

        while (true) {
            boolean progress = applyNakedSingles(work) || applyHiddenSingles(work);
            if (!progress) {
                break;
            }
        }
        return isComplete(work);
    }

    private boolean applyNakedSingles(int[][] board) {
        boolean progress = false;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] != EMPTY) {
                    continue;
                }
                int mask = getCandidateMask(board, r, c);
                if (Integer.bitCount(mask) == 1) {
                    board[r][c] = Integer.numberOfTrailingZeros(mask);
                    progress = true;
                }
            }
        }
        return progress;
    }

    private boolean applyHiddenSingles(int[][] board) {
        boolean progress = false;

        for (int row = 0; row < SIZE; row++) {
            progress = applyHiddenSingleInUnit(board, getRowUnit(row)) || progress;
        }
        for (int col = 0; col < SIZE; col++) {
            progress = applyHiddenSingleInUnit(board, getColUnit(col)) || progress;
        }
        for (int boxRow = 0; boxRow < 3; boxRow++) {
            for (int boxCol = 0; boxCol < 3; boxCol++) {
                progress = applyHiddenSingleInUnit(board, getBoxUnit(boxRow * 3, boxCol * 3)) || progress;
            }
        }
        return progress;
    }

    private boolean applyHiddenSingleInUnit(int[][] board, List<int[]> unit) {
        boolean progress = false;
        for (int digit = 1; digit <= 9; digit++) {
            int candidateCount = 0;
            int targetRow = -1;
            int targetCol = -1;

            for (int[] rc : unit) {
                int r = rc[0];
                int c = rc[1];
                if (board[r][c] != EMPTY) {
                    continue;
                }
                int mask = getCandidateMask(board, r, c);
                if ((mask & (1 << digit)) != 0) {
                    candidateCount++;
                    targetRow = r;
                    targetCol = c;
                    if (candidateCount > 1) {
                        break;
                    }
                }
            }

            if (candidateCount == 1) {
                board[targetRow][targetCol] = digit;
                progress = true;
            }
        }
        return progress;
    }

    private int getCandidateMask(int[][] board, int row, int col) {
        int mask = 0;
        for (int n = 1; n <= 9; n++) {
            if (canPlace(board, row, col, n)) {
                mask |= (1 << n);
            }
        }
        return mask;
    }

    private boolean isComplete(int[][] board) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] == EMPTY) {
                    return false;
                }
            }
        }
        return true;
    }

    private int countSolutions(int[][] board, int limit) {
        int[] best = findBestEmptyCell(board);
        if (best == null) {
            return 1;
        }
        int row = best[0];
        int col = best[1];
        int total = 0;
        for (int n = 1; n <= 9; n++) {
            if (!canPlace(board, row, col, n)) {
                continue;
            }
            board[row][col] = n;
            total += countSolutions(board, limit - total);
            if (total >= limit) {
                board[row][col] = EMPTY;
                return total;
            }
            board[row][col] = EMPTY;
        }
        return total;
    }

    private int[] findBestEmptyCell(int[][] board) {
        int bestRow = -1;
        int bestCol = -1;
        int bestCount = 10;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] != EMPTY) {
                    continue;
                }
                int candidates = 0;
                for (int n = 1; n <= 9; n++) {
                    if (canPlace(board, r, c, n)) {
                        candidates++;
                    }
                }
                if (candidates < bestCount) {
                    bestCount = candidates;
                    bestRow = r;
                    bestCol = c;
                    if (bestCount == 1) {
                        return new int[]{bestRow, bestCol};
                    }
                }
            }
        }
        if (bestRow == -1) {
            return null;
        }
        return new int[]{bestRow, bestCol};
    }

    private String formatDuration(long totalSec) {
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        return min + "분 " + sec + "초";
    }

    private void updateStageSummaryLabel() {
        int easy = clearedStagesByMode.getOrDefault("이지", 0);
        int normal = clearedStagesByMode.getOrDefault("노멀", 0);
        int hard = clearedStagesByMode.getOrDefault("하드", 0);
        stageLabel.setText("스테이지 기록 | 이지: " + easy + " | 노멀: " + normal + " | 하드: " + hard);
    }
}
