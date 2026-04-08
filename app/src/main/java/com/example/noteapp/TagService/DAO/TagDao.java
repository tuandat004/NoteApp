package com.example.noteapp.TagService.DAO;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.noteapp.NoteService.Entity.NoteTag;
import com.example.noteapp.TagService.Entity.Tag;

import java.util.List;

@Dao
public interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertTag(Tag tag);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertNoteTag(NoteTag noteTag);

    @Query("SELECT * FROM tags WHERE user_id = :userId ORDER BY tag_name ASC")
    List<Tag> getAllTags(int userId);

    @Query("SELECT * FROM tags WHERE tag_name = :tagName AND user_id = :userId LIMIT 1")
    Tag getTagByName(String tagName, int userId);

    @Query("SELECT t.* FROM tags t " +
            "INNER JOIN note_tags nt ON t.tag_id = nt.tag_id " +
            "WHERE nt.note_id = :noteId")
    List<Tag> getTagsByNoteId(int noteId);

    @Query("DELETE FROM note_tags WHERE note_id = :noteId")
    void deleteTagsOfNote(int noteId);

    @Query("DELETE FROM note_tags WHERE tag_id = :tagId")
    void deleteNoteTagsByTagId(int tagId);

    @Query("DELETE FROM tags WHERE tag_id = :tagId")
    void deleteTagById(int tagId);

    @Query("SELECT * FROM notes WHERE is_deleted = 0 AND user_id = :userId ORDER BY note_id DESC")
    List<com.example.noteapp.NoteService.Entity.Note> getAllNotesRaw(int userId);

    @Query("SELECT n.* FROM notes n " +
            "INNER JOIN note_tags nt ON n.note_id = nt.note_id " +
            "WHERE nt.tag_id = :tagId AND n.is_deleted = 0 AND n.user_id = :userId " +
            "ORDER BY n.note_id DESC")
    List<com.example.noteapp.NoteService.Entity.Note> getNotesByTagId(int tagId, int userId);
}