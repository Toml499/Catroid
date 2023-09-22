/*
 * Catroid: An on-device visual programming system for Android devices
 * Copyright (C) 2010-2023 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * An additional term exception under section 7 of the GNU Affero
 * General Public License, version 3, is available at
 * http://developer.catrobat.org/license_additional_term
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.catrobat.catroid.ui

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.appcompat.app.AlertDialog
import org.catrobat.catroid.ProjectManager
import org.catrobat.catroid.R
import org.catrobat.catroid.content.Project
import org.catrobat.catroid.content.Sprite
import org.catrobat.catroid.content.bricks.BackgroundRequestBrick
import org.catrobat.catroid.content.bricks.Brick
import org.catrobat.catroid.content.bricks.ForItemInUserListBrick
import org.catrobat.catroid.content.bricks.ForVariableFromToBrick
import org.catrobat.catroid.content.bricks.ForeverBrick
import org.catrobat.catroid.content.bricks.IfLogicBeginBrick
import org.catrobat.catroid.content.bricks.IfThenLogicBeginBrick
import org.catrobat.catroid.content.bricks.LookRequestBrick
import org.catrobat.catroid.content.bricks.OpenUrlBrick
import org.catrobat.catroid.content.bricks.ParameterizedBrick
import org.catrobat.catroid.content.bricks.PhiroIfLogicBeginBrick
import org.catrobat.catroid.content.bricks.RepeatBrick
import org.catrobat.catroid.content.bricks.RepeatUntilBrick
import org.catrobat.catroid.content.bricks.StartListeningBrick
import org.catrobat.catroid.content.bricks.WebRequestBrick
import org.koin.java.KoinJavaComponent.inject

private val projectManager by inject(ProjectManager::class.java)

private fun List<Brick>.containsStartListeningAndWebAccessBricks(): SuspiciousBricks {
    val backgroundRequestOrWebRequestBrickExists = any { brick ->
        brick is WebRequestBrick || brick is BackgroundRequestBrick || brick is LookRequestBrick || brick is OpenUrlBrick
    }
    val startListeningBrickExists = any { brick ->
        brick is StartListeningBrick
    }
    return if (startListeningBrickExists and backgroundRequestOrWebRequestBrickExists) {
        SuspiciousBricks.CONTAINS_START_LISTENING_AND_WEB_ACCESS_BRICKS
    } else if (backgroundRequestOrWebRequestBrickExists) {
        SuspiciousBricks.CONTAINS_WEB_ACCESS_BRICK
    } else {
        SuspiciousBricks.NO_SUSPICIOUS_BRICKS
    }
}

fun Sprite.getListAllBricks(): List<Brick> {
    val bricks = arrayListOf<Brick>()
    allBricks.forEach { brick ->
        bricks.add(brick)
        when (brick) {
            is ForeverBrick ->
                bricks.addAll(brick.nestedBricks)

            is IfLogicBeginBrick -> {
                bricks.addAll(brick.nestedBricks)
                bricks.addAll(brick.secondaryNestedBricks)
            }
            is IfThenLogicBeginBrick ->
                bricks.addAll(brick.nestedBricks)

            is RepeatBrick ->
                bricks.addAll(brick.nestedBricks)

            is RepeatUntilBrick ->
                bricks.addAll(brick.nestedBricks)

            is ForVariableFromToBrick ->
                bricks.addAll(brick.nestedBricks)

            is ForItemInUserListBrick ->
                bricks.addAll(brick.nestedBricks)

            is ParameterizedBrick ->
                bricks.addAll(brick.nestedBricks)

            is PhiroIfLogicBeginBrick -> {
                bricks.addAll(brick.nestedBricks)
                bricks.addAll(brick.secondaryNestedBricks)
            }
        }
    }
    return bricks
}

private fun Project.shouldDisplaySuspiciousBricksWarning(): SuspiciousBricks {
    val brickList = arrayListOf<Brick>()
    sceneList.forEach { scene ->
        brickList.run {
            scene.spriteList.forEach { sprite ->
                addAll(sprite.getListAllBricks())
            }
        }
    }
    return brickList.containsStartListeningAndWebAccessBricks()
}

fun showWarningForSuspiciousBricksOnce(context: Context) {
    val sharedPreferences = context.getSharedPreferences(
        context.getString(R.string.preference_approved_list_file_key),
        MODE_PRIVATE
    )
    val currentProject = projectManager.currentProject
    val projectUrl = currentProject?.xmlHeader?.remixParentsUrlString ?: return
    val isDownloadedProject = projectUrl.isNotBlank()
    val showForFirstTime = sharedPreferences.getString(projectUrl, null).isNullOrBlank()

    if (isDownloadedProject && showForFirstTime && currentProject.shouldDisplaySuspiciousBricksWarning() > SuspiciousBricks.NO_SUSPICIOUS_BRICKS) {
        if (currentProject.shouldDisplaySuspiciousBricksWarning() == SuspiciousBricks
                .CONTAINS_WEB_ACCESS_BRICK) {
            AlertDialog.Builder(context)
                .setTitle(context.resources.getString(R.string.warning))
                .setMessage(context.resources.getString(R.string.security_warning_dialog_msg_web_access))
                .setCancelable(false)
                .setPositiveButton(context.resources.getString(R.string.ok)) { dialog, _ ->
                    sharedPreferences
                        .edit()
                        .putString(projectUrl, projectUrl)
                        .apply()
                    dialog.dismiss()
                }
                .show()
        } else if (currentProject.shouldDisplaySuspiciousBricksWarning() == SuspiciousBricks
                .CONTAINS_START_LISTENING_AND_WEB_ACCESS_BRICKS) {
            AlertDialog.Builder(context)
                .setTitle(context.resources.getString(R.string.warning))
                .setMessage(context.resources.getString(R.string.security_warning_dialog_msg))
                .setCancelable(false)
                .setPositiveButton(context.resources.getString(R.string.ok)) { dialog, _ ->
                    sharedPreferences
                        .edit()
                        .putString(projectUrl, projectUrl)
                        .apply()
                    dialog.dismiss()
                }
                .show()
        }
    } else if (isDownloadedProject) {
        sharedPreferences
            .edit()
            .putString(projectUrl, projectUrl)
            .apply()
    }
}

enum class SuspiciousBricks {
    NO_SUSPICIOUS_BRICKS, CONTAINS_WEB_ACCESS_BRICK, CONTAINS_START_LISTENING_AND_WEB_ACCESS_BRICKS
}
