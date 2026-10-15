package com.resumecopilot.parse;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

/**
 * PDF 文本抽取（计划书第 9 章风险应对：MVP 只支持文本型 PDF）。
 *
 * <p>预处理策略：PDFTextStripper 按版式输出，简历常见"左右分栏串行粘连"，
 * W2 迭代点在此类内加 layout 模式与空行规整；当前先保证拿到全量字符并做
 * "扫描件检测"（文本量过少即拒——宁可明确失败，不做静默垃圾输入，
 * 这是和 LLM 相处工程原则第一条）。</p>
 */
@Service
public class PdfTextExtractor {

    /** 低于该字符数判定为扫描件/图片型 PDF */
    private static final int MIN_TEXT_LEN = 200;

    public String extract(File pdfFile) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            if (doc.isEncrypted()) {
                throw new IOException("加密 PDF 不支持");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);   // 按坐标排序，缓解多栏乱序
            String text = stripper.getText(doc);
            if (text.replaceAll("\\s", "").length() < MIN_TEXT_LEN) {
                throw new IOException("疑似扫描件（文本层过少），本工具 MVP 仅支持文字型 PDF");
            }
            return text;
        }
    }
}
