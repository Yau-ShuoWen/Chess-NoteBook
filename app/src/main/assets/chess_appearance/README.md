# 棋盘与棋子素材规范

棋盘和棋子必须组成一套完整主题，不再分开选择。每套主题使用一个独立目录，目录名就是应用中显示的主题名称。

目录：`themes/主题名称/`

## 棋子

每套必须包含以下 12 张图片：

- `white_king`、`white_queen`、`white_rook`、`white_bishop`、`white_knight`、`white_pawn`
- `black_king`、`black_queen`、`black_rook`、`black_bishop`、`black_knight`、`black_pawn`

文件应使用透明背景。示例：`themes/木雕/white_king.png`。

## 棋盘

纹理棋盘必须包含：

- `light`：浅色格图片
- `dark`：深色格图片

示例：`themes/胡桃木/light.webp` 和 `themes/胡桃木/dark.webp`。

纯色棋盘可以改用 `board_colors.txt`：

```text
light=#FFFFFF
dark=#F2F4F5
```

只有同时具备完整棋子和棋盘的主题才会显示在应用的“外观”列表中；素材损坏时会自动回退到经典版显示。

如需让棋子位于棋盘线交点，可在主题目录加入空文件 `grid_intersections.txt`。棋盘会连接相邻格子的中心点，并自动缩小棋子以适应交点布局。
