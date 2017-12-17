package net.dankito.deepthought.android.activities

import android.support.test.InstrumentationRegistry
import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.action.ViewActions
import android.support.test.espresso.action.ViewActions.click
import android.support.test.espresso.assertion.ViewAssertions.matches
import android.support.test.espresso.matcher.ViewMatchers.*
import android.support.test.runner.AndroidJUnit4
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import net.dankito.deepthought.android.DeepThoughtActivityTestRule
import net.dankito.deepthought.android.DeepThoughtAndroidTestBase
import net.dankito.deepthought.android.R
import net.dankito.deepthought.android.activities.arguments.EditEntryActivityParameters
import net.dankito.deepthought.android.di.TestComponent
import net.dankito.deepthought.android.service.ActivityParameterHolder
import net.dankito.deepthought.android.util.matchers.RecyclerViewInViewMatcher
import net.dankito.deepthought.android.util.matchers.RecyclerViewItemCountAssertion
import net.dankito.deepthought.android.util.screenshot.TakeScreenshotOnErrorTestRule
import net.dankito.deepthought.model.Item
import net.dankito.deepthought.model.Tag
import net.dankito.service.search.SearchEngineBase
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
class EditItemActivity_EditTagsTest : DeepThoughtAndroidTestBase() {

    companion object {
        private val PersistedTag1Name = "Persisted Tag 1"
        private val PersistedTag2Name = "Persisted Tag 2"
        private val PersistedTag3Name = "Persisted Tag 3"

        private val UnPersistedTag1Name = "Love"
        private val UnPersistedTag2Name = "Cuddle"
        private val UnPersistedTag3Name = "Hug"
    }


    @Inject
    protected lateinit var parameterHolder: ActivityParameterHolder


    private val testItem = Item("Test Content")

    private val persistedTag1 = Tag(PersistedTag1Name)

    private val persistedTag2 = Tag(PersistedTag2Name)

    private val persistedTag3 = Tag(PersistedTag3Name)


    @get:Rule
    var takeScreenshotOnError = TakeScreenshotOnErrorTestRule()

    @get:Rule
    val testRule = DeepThoughtActivityTestRule<EditEntryActivity>(EditEntryActivity::class.java)


    init {
        TestComponent.component.inject(this)

        persistTag(persistedTag1)
        persistTag(persistedTag2)
        persistTag(persistedTag3)

        testItem.addTag(persistedTag1)
        testItem.addTag(persistedTag2)
        testItem.addTag(persistedTag3)

        testRule.setActivityParameter(parameterHolder, EditEntryActivityParameters(testItem))
    }


    @Test
    fun addNotPersistedTags_TagsGetSaved() {
        assertThat(testItem.tags.size, `is`(3))
        checkDisplayedPreviewTagsMatch(*testItem.tags.toTypedArray())

        onView(withId(R.id.lytTagsPreview)).perform(ViewActions.click())

        navigator.enterText(UnPersistedTag1Name)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag1Name), *testItem.tags.toTypedArray())

        navigator.enterText(SearchEngineBase.TagsSearchTermSeparator)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag1Name), *testItem.tags.toTypedArray())

        navigator.enterText(UnPersistedTag2Name)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag1Name), Tag(UnPersistedTag2Name), *testItem.tags.toTypedArray())

        navigator.enterText(SearchEngineBase.TagsSearchTermSeparator)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag1Name), Tag(UnPersistedTag2Name), *testItem.tags.toTypedArray())

        navigator.enterText(UnPersistedTag3Name)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag1Name), Tag(UnPersistedTag2Name), Tag(UnPersistedTag3Name), *testItem.tags.toTypedArray())

        navigator.enterText(SearchEngineBase.TagsSearchTermSeparator)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag1Name), Tag(UnPersistedTag2Name), Tag(UnPersistedTag3Name), *testItem.tags.toTypedArray())

        assertThat(testItem.tags.size, `is`(3)) // Item is not saved yet, but displayed tags preview must get updated

        onView(withId(R.id.mnSaveEntry)).perform(click())
        assertThat(testItem.tags.size, `is`(6))
        testItem.tags.forEach { tag ->
            assertThat(tag.isPersisted(), `is`(true))
        }
    }


    @Test
    fun addPersistedAndNotPersistedTags_TagsGetSaved() {
        assertThat(testItem.tags.size, `is`(3))
        checkDisplayedPreviewTagsMatch(*testItem.tags.toTypedArray())

        onView(withId(R.id.lytTagsPreview)).perform(ViewActions.click())

        navigator.enterText(UnPersistedTag1Name)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag1Name), *testItem.tags.toTypedArray())
        checkCountItemsInRecyclerViewTagSearchResults(0)

        navigator.enterText(SearchEngineBase.TagsSearchTermSeparator)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag1Name), *testItem.tags.toTypedArray())
        checkCountItemsInRecyclerViewTagSearchResults(0)

        navigator.enterText(PersistedTag1Name)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag1Name), *testItem.tags.toTypedArray()) // PersistedTag1Name may not gets displayed twice
        checkCountItemsInRecyclerViewTagSearchResults(1)

        navigator.enterText(SearchEngineBase.TagsSearchTermSeparator)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag1Name), *testItem.tags.toTypedArray())
        checkCountItemsInRecyclerViewTagSearchResults(1)

        navigator.enterText(UnPersistedTag3Name)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag1Name), Tag(UnPersistedTag3Name), *testItem.tags.toTypedArray())
        checkCountItemsInRecyclerViewTagSearchResults(0)

        navigator.enterText(SearchEngineBase.TagsSearchTermSeparator)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag1Name), Tag(UnPersistedTag3Name), *testItem.tags.toTypedArray())
        checkCountItemsInRecyclerViewTagSearchResults(0)

        navigator.enterText(PersistedTag3Name)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag1Name), Tag(UnPersistedTag3Name), *testItem.tags.toTypedArray())
        checkCountItemsInRecyclerViewTagSearchResults(1)

        navigator.enterText(SearchEngineBase.TagsSearchTermSeparator)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag1Name), Tag(UnPersistedTag3Name), *testItem.tags.toTypedArray())
        checkCountItemsInRecyclerViewTagSearchResults(1)

        assertThat(testItem.tags.size, `is`(3)) // Item is not saved yet, but displayed tags preview must get updated

        onView(withId(R.id.mnSaveEntry)).perform(click())
        assertThat(testItem.tags.size, `is`(5))
        testItem.tags.forEach { tag ->
            assertThat(tag.isPersisted(), `is`(true))
        }
    }


    @Test
    fun removedEnteredTag_RemovedTagIsAtBeginningOfSearchTerm() {
        assertThat(testItem.tags.size, `is`(3))
        checkDisplayedPreviewTagsMatch(*testItem.tags.toTypedArray())

        onView(withId(R.id.lytTagsPreview)).perform(ViewActions.click())

        navigator.enterText(PersistedTag1Name.toLowerCase())
        navigator.enterText(SearchEngineBase.TagsSearchTermSeparator)
        checkDisplayedPreviewTagsMatch(*testItem.tags.toTypedArray())
        checkCountItemsInRecyclerViewTagSearchResults(1)

        navigator.enterText(UnPersistedTag1Name)
        navigator.enterText(SearchEngineBase.TagsSearchTermSeparator)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag1Name), *testItem.tags.toTypedArray())
        checkCountItemsInRecyclerViewTagSearchResults(0)

        navigator.enterText(UnPersistedTag2Name)
        navigator.enterText(SearchEngineBase.TagsSearchTermSeparator)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag2Name), Tag(UnPersistedTag1Name), *testItem.tags.toTypedArray())
        checkCountItemsInRecyclerViewTagSearchResults(0)


        clickOnFirstDisplayedTagSearchResult()


        assertPersistedTag1GotRemoved()
    }


    @Test
    fun removedEnteredTag_RemovedTagIsInTheMiddleOfSearchTerm() {
        assertThat(testItem.tags.size, `is`(3))
        checkDisplayedPreviewTagsMatch(*testItem.tags.toTypedArray())

        onView(withId(R.id.lytTagsPreview)).perform(ViewActions.click())

        navigator.enterText(UnPersistedTag1Name)
        navigator.enterText(SearchEngineBase.TagsSearchTermSeparator)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag1Name), *testItem.tags.toTypedArray())
        checkCountItemsInRecyclerViewTagSearchResults(0)

        // Tag to remove soon
        navigator.enterText(PersistedTag1Name.toLowerCase())
        navigator.enterText(SearchEngineBase.TagsSearchTermSeparator)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag1Name), *testItem.tags.toTypedArray())
        checkCountItemsInRecyclerViewTagSearchResults(1)

        navigator.enterText(UnPersistedTag2Name)
        navigator.enterText(SearchEngineBase.TagsSearchTermSeparator)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag2Name), Tag(UnPersistedTag1Name), *testItem.tags.toTypedArray())
        checkCountItemsInRecyclerViewTagSearchResults(0)


        clickOnFirstDisplayedTagSearchResult()


        assertPersistedTag1GotRemoved()
    }

    @Test
    fun removedEnteredTag_RemovedTagIsAtEndOfSearchTerm() {
        assertThat(testItem.tags.size, `is`(3))
        checkDisplayedPreviewTagsMatch(*testItem.tags.toTypedArray())

        onView(withId(R.id.lytTagsPreview)).perform(ViewActions.click())

        navigator.enterText(UnPersistedTag1Name)
        navigator.enterText(SearchEngineBase.TagsSearchTermSeparator)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag1Name), *testItem.tags.toTypedArray())
        checkCountItemsInRecyclerViewTagSearchResults(0)

        navigator.enterText(UnPersistedTag2Name)
        navigator.enterText(SearchEngineBase.TagsSearchTermSeparator)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag2Name), Tag(UnPersistedTag1Name), *testItem.tags.toTypedArray())
        checkCountItemsInRecyclerViewTagSearchResults(0)

        navigator.enterText(PersistedTag1Name.toLowerCase())
        navigator.enterText(SearchEngineBase.TagsSearchTermSeparator)
        checkDisplayedPreviewTagsMatch(Tag(UnPersistedTag2Name), Tag(UnPersistedTag1Name), *testItem.tags.toTypedArray())
        checkCountItemsInRecyclerViewTagSearchResults(1)


        clickOnFirstDisplayedTagSearchResult()


        assertPersistedTag1GotRemoved()
    }

    private fun assertPersistedTag1GotRemoved() {
        val newDisplayedTags = ArrayList(testItem.tags)
        newDisplayedTags.remove(persistedTag1)
        newDisplayedTags.add(Tag(UnPersistedTag1Name))
        newDisplayedTags.add(Tag(UnPersistedTag2Name))
        checkDisplayedPreviewTagsMatch(*newDisplayedTags.toTypedArray())

        // now check if PersistedTag1Name got removed from edtxtEntityFieldValue
        removeWhitespacesEnteredByKeyboardApp() // my keyboard app enters after each comma a white space
        onView(allOf(withId(R.id.edtxtEntityFieldValue), isDescendantOfA(withId(R.id.lytTagsPreview))))
                .check(matches(withText(`is`("$UnPersistedTag1Name${SearchEngineBase.TagsSearchTermSeparator}" +
                        "$UnPersistedTag2Name${SearchEngineBase.TagsSearchTermSeparator}"))))

        onView(withId(R.id.mnSaveEntry)).perform(click())
        assertThat(testItem.tags.size, `is`(newDisplayedTags.size))
        testItem.tags.forEach { tag ->
            assertThat(tag.isPersisted(), `is`(true))
        }
    }

    private fun clickOnFirstDisplayedTagSearchResult() {
        onView(RecyclerViewInViewMatcher.withRecyclerView(R.id.lytTagsPreview, R.id.rcySearchResults)
                .atPosition(0))
                .perform(click())
    }


    private fun checkDisplayedPreviewTagsMatch(vararg tags: Tag) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val lytTagsPreview = testRule.activity.findViewById(R.id.lytTagsPreview) as? ViewGroup
            val lytCollectionPreview = lytTagsPreview?.findViewById(R.id.lytCollectionPreview) as? ViewGroup

            lytCollectionPreview?.let {
                val displayedTagNames = ArrayList<String>()

                for(i in 0..lytCollectionPreview.childCount) {
                    val tagView = lytCollectionPreview.getChildAt(i)
                    (tagView?.findViewById(R.id.txtTagName) as? TextView)?.text?.let { tagName -> displayedTagNames.add(tagName.toString()) }
                }

                tags.forEach { tag ->
                    assertThat("Tag name ${tag.name} not found in displayed tag names $displayedTagNames", displayedTagNames.contains(tag.name), `is`(true))
                }

                displayedTagNames.removeAll(tags.map { it.name }) // assert that no other tags then in tags are displayed
                assertThat("Tags that are displayed but shouldn't: $displayedTagNames", displayedTagNames.size, `is`(0))
            }
        }
    }

    private fun removeWhitespacesEnteredByKeyboardApp() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val lytTagsPreview = testRule.activity.findViewById(R.id.lytTagsPreview) as? ViewGroup
            val edtxtEntityFieldValue = lytTagsPreview?.findViewById(R.id.edtxtEntityFieldValue) as? EditText

            edtxtEntityFieldValue?.let {
                edtxtEntityFieldValue.setText(edtxtEntityFieldValue.text.toString().replace(", ", ","))
            }
        }
    }

    private fun checkCountItemsInRecyclerViewTagSearchResults(expectedCount: Int) {
        onView(allOf(withId(R.id.rcySearchResults), isDescendantOfA(withId(R.id.lytTagsPreview))))
                .check(RecyclerViewItemCountAssertion(expectedCount))
    }

    private fun checkDisplayedTagsValue(tags: Collection<Tag>) {
        checkDisplayedTagsValue(tags.sortedBy { it.name }.joinToString { it.name })
    }

    private fun checkDisplayedTagsValue(tagsDisplayName: String) {
        checkDisplayedValueInEditEntityField(tagsDisplayName, R.id.lytTagsPreview)
    }

    private fun checkDisplayedValueInEditEntityField(valueToMatch: String, editEntityFieldId: Int) {
        onView(allOf(withId(R.id.edtxtEntityFieldValue), isDescendantOfA(withId(editEntityFieldId)))) // find edtxtEntityFieldValue in EditEntityField
                .check(matches(withText(`is`(valueToMatch))))
    }

}