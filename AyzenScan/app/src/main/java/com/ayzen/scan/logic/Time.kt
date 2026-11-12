package com.ayzen.scan.logic

import java.text.DateFormat
import java.util.Date

fun formatTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
