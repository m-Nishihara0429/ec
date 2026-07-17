package com.example.ec.controller.admin;

import org.springframework.dao.DataAccessException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 管理系コントローラーの削除処理で共通する「削除を試み、既に削除済み・他レコードから
 * 参照中などでDataAccessExceptionが発生した場合はエラーメッセージ付きで一覧画面に戻す」
 * という定型パターンをまとめたユーティリティ。
 * AdminProductController・AdminCategoryController・AdminCouponController・
 * AdminFaqController・AdminInquiryControllerのdeleteメソッドがそれぞれ同じ形の
 * try/catchを持っていたため、ここに集約している。
 */
final class AdminControllerSupport {

    private AdminControllerSupport() {
    }

    /**
     * 削除処理を実行し、DataAccessExceptionが発生した場合はフラッシュ属性にエラーメッセージを設定する。
     *
     * @param deleteAction       実行する削除処理（例: {@code () -> productService.deleteById(id)}）
     * @param redirectAttributes エラーメッセージを設定するフラッシュ属性
     * @param errorMessage       削除失敗時に表示するエラーメッセージ
     */
    static void safeDelete(Runnable deleteAction, RedirectAttributes redirectAttributes, String errorMessage) {
        try {
            deleteAction.run();
        } catch (DataAccessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
        }
    }
}
