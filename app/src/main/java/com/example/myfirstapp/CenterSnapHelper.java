package com.example.myfirstapp;

import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

public class CenterSnapHelper extends LinearSnapHelper {
    // LinearSnapHelper уже содержит идеальную физику для "прилипания" к центру.
    // Мы наследуемся от него, чтобы в будущем добавить сюда вибрацию или звук при привязке.
}