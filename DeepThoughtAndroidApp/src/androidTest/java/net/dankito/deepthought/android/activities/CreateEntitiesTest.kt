package net.dankito.deepthought.android.activities

import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.action.ViewActions.click
import android.support.test.espresso.action.ViewActions.swipeLeft
import android.support.test.espresso.assertion.ViewAssertions.matches
import android.support.test.espresso.matcher.ViewMatchers.*
import android.support.test.runner.AndroidJUnit4
import net.dankito.deepthought.android.DeepThoughtActivityTestRule
import net.dankito.deepthought.android.DeepThoughtAndroidTestBase
import net.dankito.deepthought.android.MainActivity
import net.dankito.deepthought.android.R
import net.dankito.deepthought.android.fragments.ItemsListView
import net.dankito.deepthought.android.fragments.SourcesListView
import net.dankito.deepthought.android.fragments.TagsListView
import net.dankito.deepthought.android.util.TestUtil
import net.dankito.deepthought.android.util.matchers.RecyclerViewInViewPagerMatcher.Companion.withRecyclerView
import net.dankito.deepthought.android.util.matchers.RecyclerViewItemCountAssertion
import net.dankito.deepthought.android.util.matchers.RecyclerViewMatcher
import net.dankito.deepthought.android.util.screenshot.TakeScreenshotOnErrorTestRule
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateEntitiesTest : DeepThoughtAndroidTestBase() {

    companion object {
        const val TestContent = "Test Content"
        const val TestSummary = "Just a summary"

        const val TestSourceTitle = "Source of it all"
        const val TestSeriesTitle = "What a series"

        const val TestTag1Name = "Tag uno"
        const val TestTag2Name = "Tag due"
        const val TestTag3Name = "Tag tre"

        const val TestFeedUrl = "heise.de"
        const val TestFeedName = "Heise"
    }


    @get:Rule
    val testRule = DeepThoughtActivityTestRule<MainActivity>(MainActivity::class.java)

    @get:Rule
    var takeScreenshotOnError = TakeScreenshotOnErrorTestRule()


    @Test
    fun createItemTagsSourceAndSeries() {
        navigator.createEntities(TestContent, TestSummary, TestSourceTitle, TestSeriesTitle, TestTag1Name, TestTag2Name, TestTag3Name)
        TestUtil.sleep(1000)


        matchRecyclerViewEntitiesIsDisplayed()

        matchSourcePreviewAtPosition(0, TestSourceTitle)

        matchItemPreviewAtPositionContains(0, TestContent)
        matchItemPreviewAtPositionContains(0, TestSummary)
        matchItemPreviewAtPositionContains(0, TestSeriesTitle)

        // TODO: how to match tags?
//        onView(withRecyclerView(R.id.rcyEntities).atPositionOnView(0, R.id.lytItemTags)).check(matches(withText(containsString(TestTag1Name))))


        navigator.navigateToTabTags()

        matchRecyclerViewEntitiesIsDisplayed()

        matchTagNameAtPosition(2, TestTag2Name)
        matchTagNameAtPosition(3, TestTag3Name)
        matchTagNameAtPosition(4, TestTag1Name)


        navigator.navigateToTabSources()

        matchRecyclerViewEntitiesIsDisplayed()

        matchListItemAtPositionText(SourcesListView::class.java, 0, R.id.txtvwEntityName, TestSourceTitle)
        matchListItemAtPositionText(SourcesListView::class.java, 0, R.id.txtvwEntitySecondaryInformation, TestSeriesTitle)
    }


    @Test
    fun createReadLaterArticleAndNewsPaperItem() {
        matchRecyclerViewHasCountItems(0)


        navigator.createRssFeed(TestFeedUrl, TestFeedName)

        clickOnArticleSummaryActivitySwipeButtonAtPosition(0, R.id.btnSaveArticleSummaryItemOrReadLaterArticle)

        clickOnArticleSummaryActivitySwipeButtonAtPosition(1, R.id.btnSaveArticleSummaryItemForLaterReading)
        TestUtil.sleep(1000)

        navigator.pressBack()
        TestUtil.sleep(500)

        matchRecyclerViewHasCountItems(1)


        navigator.navigateToTabReadLaterArticles()

        matchRecyclerViewHasCountItems(1)
    }


    private fun matchRecyclerViewEntitiesIsDisplayed() {
        onView(allOf(withId(R.id.rcyEntities))).check(matches(isDisplayed()))
    }

    private fun matchItemPreviewAtPositionContains(position: Int, itemPreview: String) {
        matchListItemAtPositionText(ItemsListView::class.java, position, R.id.txtItemPreview, itemPreview)
    }

    private fun matchSourcePreviewAtPosition(position: Int, sourcePreview: String) {
        matchListItemAtPositionText(ItemsListView::class.java, position, R.id.txtSourcePreview, sourcePreview)
    }

    private fun matchTagNameAtPosition(position: Int, tagName: String) {
        matchListItemAtPositionText(TagsListView::class.java, position, R.id.txtTagDisplayText, tagName)
    }

    private fun matchListItemAtPositionText(tabTag: Any, position: Int, textViewResourceId: Int, textViewText: String) {
        onView(withRecyclerView(tabTag, R.id.rcyEntities).atPositionOnView(position, textViewResourceId))
                .check(matches(withText(containsString(textViewText))))
    }

    private fun matchRecyclerViewHasCountItems(expectedCount: Int) {
        onView(allOf(withId(R.id.rcyEntities))).check(RecyclerViewItemCountAssertion(expectedCount))
    }


    private fun clickOnArticleSummaryActivitySwipeButtonAtPosition(position: Int, buttonId: Int) {
        clickOnSwipeButtonAtPosition(R.id.rcyArticleSummaryItems, position, buttonId)
    }

    private fun clickOnSwipeButtonAtPosition(recyclerViewId: Int, position: Int, buttonId: Int) {
        onView(RecyclerViewMatcher.withRecyclerView(recyclerViewId).atPosition(position))
                .perform(swipeLeft())
        TestUtil.sleep(1000)

        onView(RecyclerViewMatcher.withRecyclerView(recyclerViewId).atPositionOnView(position, buttonId))
                .perform(click())
    }

}