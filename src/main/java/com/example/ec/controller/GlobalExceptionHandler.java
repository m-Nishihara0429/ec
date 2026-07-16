package com.example.ec.controller;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 各コントローラーでローカルに catch されなかった業務例外の最終防波堤。
 * findById 系のメソッド（ProductService.findById, OrderService.findById 等）は、
 * 存在しないIDを指定されると IllegalArgumentException を投げる設計になっているが、
 * 一部のGETエンドポイント（例: 商品詳細、注文詳細、カテゴリ編集画面）ではこれをローカルで
 * catch しておらず、素の500エラー（Spring Bootの標準エラーページ）がそのまま表示されていた。
 * ここでアプリ全体に対する最後の受け皿として、404相当の分かりやすいページに変換する。
 * ローカルの try/catch がある箇所ではそちらが先に処理するため、ここには到達しない。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(IllegalArgumentException e, Model model) {
        model.addAttribute("message", e.getMessage());
        return "error/not_found";
    }
}
